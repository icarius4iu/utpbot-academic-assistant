"""
Servicio de integración con Telegram para el sistema UTPBot.
Maneja sesiones de usuario por chat_id y conecta con Gemini.
"""

import os
import httpx
import logging
from typing import Dict, Optional
from services.sheets_service import sheets_service
from services.gemini_service import gemini_service
from utils.prompt_builder import construir_prompt_sistema

logger = logging.getLogger(__name__)

TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_API = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}"

# Mensajes de bienvenida / ayuda
MENSAJE_BIENVENIDA = """
👋 ¡Hola! Soy el *Asistente Académico Virtual UTP* 🎓

Puedo ayudarte con consultas sobre:
📚 Horarios de clases
📝 Notas y evaluaciones  
📅 Calendario académico
🏫 Trámites y servicios UTP
ℹ️ Información general universitaria

*¿Cómo usar el bot?*
1️⃣ Escribe tu código institucional (Ej: `E001`)
2️⃣ Confirma tu identidad
3️⃣ ¡Haz tus consultas directamente!

O simplemente escribe tu pregunta y te identifico como visitante 👤

Usa /ayuda para ver este mensaje nuevamente.
"""

MENSAJE_AYUDA = """
🤖 *Comandos disponibles:*

/start — Iniciar el bot
/ayuda — Ver esta ayuda
/identificar — Vincular tu código UTP
/nueva — Iniciar una nueva conversación
/salir — Cerrar tu sesión

📌 *Tip:* También puedes enviarme documentos PDF y te ayudaré a analizarlos.
"""


class TelegramSession:
    """Representa la sesión de un usuario en Telegram."""
    def __init__(self, chat_id: int):
        self.chat_id = chat_id
        self.codigo_utp: Optional[str] = None
        self.rol: str = "estudiante"
        self.nombre: str = "Visitante"
        self.historial: list = []
        self.esperando_codigo: bool = False


class TelegramService:
    """
    Servicio que gestiona sesiones de Telegram y conecta con Gemini.
    Mantiene un diccionario en memoria de sesiones activas por chat_id.
    """

    def __init__(self):
        # Diccionario de sesiones activas: {chat_id: TelegramSession}
        self._sesiones: Dict[int, TelegramSession] = {}

    def _obtener_sesion(self, chat_id: int) -> TelegramSession:
        """Obtiene o crea la sesión de un usuario."""
        if chat_id not in self._sesiones:
            self._sesiones[chat_id] = TelegramSession(chat_id)
        return self._sesiones[chat_id]

    async def enviar_mensaje(self, chat_id: int, texto: str, parse_mode: str = "Markdown") -> bool:
        """Envía un mensaje de texto a un chat de Telegram."""
        if not TELEGRAM_BOT_TOKEN:
            logger.warning("TELEGRAM_BOT_TOKEN no configurado")
            return False

        payload = {
            "chat_id": chat_id,
            "text": texto,
            "parse_mode": parse_mode,
        }
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                resp = await client.post(f"{TELEGRAM_API}/sendMessage", json=payload)
                resp.raise_for_status()
                return True
        except Exception as e:
            logger.error(f"Error enviando mensaje Telegram a {chat_id}: {e}")
            return False

    async def enviar_typing(self, chat_id: int):
        """Muestra indicador 'escribiendo...' en el chat."""
        if not TELEGRAM_BOT_TOKEN:
            return
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                await client.post(
                    f"{TELEGRAM_API}/sendChatAction",
                    json={"chat_id": chat_id, "action": "typing"}
                )
        except Exception:
            pass

    async def configurar_webhook(self, webhook_url: str) -> dict:
        """Configura el webhook de Telegram apuntando al backend."""
        if not TELEGRAM_BOT_TOKEN:
            return {"ok": False, "description": "Token no configurado"}

        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                resp = await client.post(
                    f"{TELEGRAM_API}/setWebhook",
                    json={"url": webhook_url, "allowed_updates": ["message", "callback_query"]}
                )
                return resp.json()
        except Exception as e:
            return {"ok": False, "description": str(e)}

    async def procesar_mensaje(self, chat_id: int, texto: str, nombre_telegram: str = ""):
        """
        Procesa un mensaje entrante de Telegram:
        - Maneja comandos especiales
        - Si el usuario está identificado, genera respuesta con Gemini
        - Si no, solicita identificación
        """
        sesion = self._obtener_sesion(chat_id)
        texto_limpio = texto.strip()

        # ── Comandos ─────────────────────────────────────────────────
        if texto_limpio in ("/start", "/inicio"):
            sesion.historial = []
            sesion.esperando_codigo = False
            await self.enviar_mensaje(chat_id, MENSAJE_BIENVENIDA)
            return

        if texto_limpio in ("/ayuda", "/help"):
            await self.enviar_mensaje(chat_id, MENSAJE_AYUDA)
            return

        if texto_limpio in ("/nueva", "/new"):
            sesion.historial = []
            await self.enviar_mensaje(chat_id, "🔄 Conversación reiniciada. ¿En qué puedo ayudarte?")
            return

        if texto_limpio in ("/salir", "/logout"):
            sesion.codigo_utp = None
            sesion.historial = []
            sesion.rol = "estudiante"
            sesion.nombre = "Visitante"
            await self.enviar_mensaje(chat_id, "👋 Sesión cerrada. Usa /start para volver a empezar.")
            return

        if texto_limpio == "/identificar":
            sesion.esperando_codigo = True
            await self.enviar_mensaje(
                chat_id,
                "🔑 Por favor, escribe tu *código institucional UTP*:\n"
                "_(Ejemplo: E001, D002)_"
            )
            return

        # ── Flujo de identificación ───────────────────────────────────
        if sesion.esperando_codigo:
            codigo = texto_limpio.upper()
            sesion.esperando_codigo = False

            datos_usuario = None
            rol_encontrado = None
            nombre_encontrado = "Visitante"

            try:
                est = sheets_service.buscar_estudiante(codigo)
                if est:
                    datos_usuario = est
                    rol_encontrado = "estudiante"
                    nombre_encontrado = est.get("nombre", "Estudiante")
                else:
                    doc = sheets_service.buscar_docente(codigo)
                    if doc:
                        datos_usuario = doc
                        rol_encontrado = "docente"
                        nombre_encontrado = doc.get("nombre", "Docente")
            except Exception as e:
                logger.warning(f"Sheets no disponible en Telegram: {e}")
                # Demo fallback
                demo_est = {"E001": "Ana García", "E002": "Carlos Mendoza", "E003": "Lucía Torres"}
                demo_doc = {"D001": "Dr. Roberto Flores", "D002": "Mg. Carmen Salinas"}
                if codigo in demo_est:
                    nombre_encontrado = demo_est[codigo]
                    rol_encontrado = "estudiante"
                elif codigo in demo_doc:
                    nombre_encontrado = demo_doc[codigo]
                    rol_encontrado = "docente"

            if rol_encontrado:
                sesion.codigo_utp = codigo
                sesion.rol = rol_encontrado
                sesion.nombre = nombre_encontrado
                sesion.historial = []
                await self.enviar_mensaje(
                    chat_id,
                    f"✅ ¡Bienvenido/a, *{nombre_encontrado}*!\n"
                    f"Rol: *{rol_encontrado.capitalize()}*\n\n"
                    f"Ahora puedes hacerme tus consultas académicas 📚"
                )
            else:
                await self.enviar_mensaje(
                    chat_id,
                    f"❌ No encontré el código *{codigo}*.\n"
                    "Verifica tu código institucional o usa /ayuda."
                )
            return

        # ── Generar respuesta con Gemini ──────────────────────────────
        await self.enviar_typing(chat_id)

        codigo = sesion.codigo_utp or "TELEGRAM_ANON"
        rol = sesion.rol

        try:
            if sesion.codigo_utp:
                if rol == "estudiante":
                    datos_usuario = sheets_service.recopilar_datos_estudiante(codigo)
                else:
                    datos_usuario = sheets_service.recopilar_datos_docente(codigo)
            else:
                datos_usuario = {}

            system_prompt = construir_prompt_sistema(
                rol=rol,
                nombre=sesion.nombre,
                datos_usuario=datos_usuario,
                idioma="es",
                es_primer_mensaje=(len(sesion.historial) == 0)
            )

            respuesta, _ = gemini_service.generar_respuesta(
                system_prompt=system_prompt,
                mensaje_usuario=texto_limpio,
                historial=sesion.historial,
                datos_usuario=datos_usuario,
            )

            # Actualizar historial (mantener máximo 10 turnos)
            sesion.historial.append({"rol": "user", "contenido": texto_limpio})
            sesion.historial.append({"rol": "assistant", "contenido": respuesta})
            if len(sesion.historial) > 20:
                sesion.historial = sesion.historial[-20:]

            # Enviar respuesta (Telegram tiene límite de 4096 chars)
            if len(respuesta) > 4000:
                partes = [respuesta[i:i+4000] for i in range(0, len(respuesta), 4000)]
                for parte in partes:
                    await self.enviar_mensaje(chat_id, parte)
            else:
                await self.enviar_mensaje(chat_id, respuesta)

        except Exception as e:
            logger.error(f"Error procesando mensaje Telegram: {e}")
            await self.enviar_mensaje(
                chat_id,
                "⚠️ Ocurrió un error al procesar tu consulta. Por favor, intenta nuevamente."
            )


# Instancia global (singleton)
telegram_service = TelegramService()
