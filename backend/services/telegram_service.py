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

# Mensajes de bienvenida / ayuda en modo notificación
MENSAJE_BIENVENIDA = """
🤖 *Bot de Mensajería y Notificaciones UTP IA* 🎓

¡Hola! Este canal sirve exclusivamente para enviarte **notificaciones automáticas, alertas y confirmaciones** en tiempo real. 

Por ejemplo, cuando uses el Asistente UTP IA en la Web para organizar tu tiempo de estudio, este bot te enviará un mensaje de confirmación cuando los bloques de estudio sean añadidos a tu **Google Calendar** 📅.

🌐 *¿Quieres chatear con la IA o planificar tus horarios?*
Accede ahora a la plataforma web oficial de **UTP IA**.
"""

MENSAJE_AYUDA = """
🤖 *Bot de Notificaciones UTP IA*

Este bot está configurado en modo **Notificación**. No procesa consultas interactivas de forma directa en Telegram para garantizar un canal limpio y libre de spam.

📌 *¿Cómo funciona?*
1. Entra al sitio web del **Asistente Académico UTP IA**.
2. Planifica tus sesiones de estudio o revisa tus exámenes.
3. Al agendar tus sesiones de estudio en **Google Calendar**, recibirás una alerta de confirmación instantánea aquí.
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
        Procesa un mensaje entrante de Telegram en modo notificación:
        - Responde con instrucciones y enlaces al sitio web.
        """
        texto_limpio = texto.strip().lower()

        # Enviar comandos de bienvenida o ayuda de forma formal
        if texto_limpio in ("/start", "/inicio"):
            await self.enviar_mensaje(chat_id, MENSAJE_BIENVENIDA)
            return

        if texto_limpio in ("/ayuda", "/help"):
            await self.enviar_mensaje(chat_id, MENSAJE_AYUDA)
            return

        # Para cualquier otro mensaje, recordar cortésmente que es un bot de notificaciones
        recordatorio = (
            "🤖 *Bot de Notificaciones UTP IA* 🎓\n\n"
            "Hola. Este canal está reservado exclusivamente para el envío de **notificaciones, alertas y confirmaciones** automáticas.\n\n"
            "Si deseas chatear con la Inteligencia Artificial, organizar tus horarios de estudio en Google Calendar o revisar tu avance, ingresa a la plataforma web oficial 🌐."
        )
        await self.enviar_mensaje(chat_id, recordatorio)

    async def enviar_confirmacion_estudio(
        self,
        titulo: str,
        fecha_inicio: str,  # Formato ISO 8601, ej: "2026-06-03T16:00:00-05:00"
        fecha_fin: str,     # Formato ISO 8601, ej: "2026-06-03T18:00:00-05:00"
        descripcion: str = "",
        chat_id: Optional[int] = None
    ) -> bool:
        """
        Envía un mensaje enriquecido con Markdown que confirma el éxito del agendamiento en Google Calendar.
        """
        target_chat_id = chat_id or os.getenv("TELEGRAM_NOTIFICATIONS_CHAT_ID")
        if not target_chat_id:
            logger.warning("TELEGRAM_NOTIFICATIONS_CHAT_ID no configurado y no se pasó un chat_id")
            return False

        try:
            target_chat_id = int(target_chat_id)
        except ValueError:
            logger.error(f"TELEGRAM_NOTIFICATIONS_CHAT_ID no es un entero válido: {target_chat_id}")
            return False

        # Formatear fecha y horas legibles
        # Ejemplo: "2026-06-03T16:00:00-05:00" -> fecha: "2026-06-03", horas: "16:00" y "18:00"
        try:
            fecha_str = fecha_inicio.split("T")[0]
            hora_inicio = fecha_inicio.split("T")[1][:5]
            hora_fin = fecha_fin.split("T")[1][:5]
            
            # Formatear fecha a DD/MM/YYYY
            año, mes, día = fecha_str.split("-")
            fecha_legible = f"{día}/{mes}/{año}"
        except Exception:
            fecha_legible = fecha_inicio
            hora_inicio = "Ver evento"
            hora_fin = "Ver evento"

        mensaje_alert = (
            f"📅 *¡NUEVO BLOQUE DE ESTUDIO AGENDADO!* 📅\n\n"
            f"Hola, el *Asistente Académico UTP IA* ha reservado un bloque de tiempo de estudio en tu **Google Calendar**:\n\n"
            f"📖 *Tema:* `{titulo}`\n"
            f"📅 *Fecha:* {fecha_legible}\n"
            f"⏰ *Horario:* {hora_inicio} - {hora_fin}\n\n"
            f"📝 *Detalles:* {descripcion or 'Sin descripción adicional.'}\n\n"
            f"🚀 _¡Mucho éxito en tu sesión! Organizar tu tiempo con anticipación te garantizará mejores resultados en tus evaluaciones._"
        )

        return await self.enviar_mensaje(chat_id=target_chat_id, texto=mensaje_alert, parse_mode="Markdown")


# Instancia global (singleton)
telegram_service = TelegramService()

