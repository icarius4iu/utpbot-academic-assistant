"""
Rutas del webhook de Telegram para el sistema UTPBot.
Recibe updates de Telegram y los procesa con el TelegramService.
"""

import os
import logging
from fastapi import APIRouter, HTTPException, Request, BackgroundTasks
from services.telegram_service import telegram_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/telegram", tags=["Telegram"])

TELEGRAM_WEBHOOK_URL = os.getenv("TELEGRAM_WEBHOOK_URL", "")
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")


@router.post("/webhook")
async def telegram_webhook(request: Request, background_tasks: BackgroundTasks):
    """
    Endpoint webhook de Telegram.
    Telegram llama a este endpoint cada vez que llega un mensaje al bot.
    Se procesa en background para responder rápido (< 5s requerido por Telegram).
    """
    try:
        update = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="JSON inválido")

    # Extraer mensaje del update
    message = update.get("message") or update.get("edited_message")

    if not message:
        # Ignorar updates sin mensaje (callbacks, etc.) sin error
        return {"ok": True}

    chat_id = message.get("chat", {}).get("id")
    texto = message.get("text", "")
    nombre = message.get("from", {}).get("first_name", "")

    if not chat_id or not texto:
        return {"ok": True}

    # Procesar en background para responder inmediatamente a Telegram
    background_tasks.add_task(
        telegram_service.procesar_mensaje,
        chat_id=chat_id,
        texto=texto,
        nombre_telegram=nombre
    )

    return {"ok": True}


@router.api_route("/setup-webhook", methods=["GET", "POST"])
async def configurar_webhook():
    """
    Configura el webhook de Telegram apuntando a este servidor.
    Debe llamarse una vez después del deploy.
    Requiere que TELEGRAM_WEBHOOK_URL esté configurado en el .env
    """
    if not TELEGRAM_BOT_TOKEN:
        raise HTTPException(
            status_code=503,
            detail="TELEGRAM_BOT_TOKEN no configurado en las variables de entorno."
        )

    if not TELEGRAM_WEBHOOK_URL:
        raise HTTPException(
            status_code=503,
            detail="TELEGRAM_WEBHOOK_URL no configurado en las variables de entorno."
        )

    result = await telegram_service.configurar_webhook(TELEGRAM_WEBHOOK_URL)

    if result.get("ok"):
        return {
            "success": True,
            "message": f"Webhook configurado correctamente en: {TELEGRAM_WEBHOOK_URL}",
            "result": result
        }
    else:
        raise HTTPException(
            status_code=500,
            detail=f"Error configurando webhook: {result.get('description', 'Unknown error')}"
        )


@router.get("/status")
async def estado_telegram():
    """
    Verifica el estado de la configuración del bot de Telegram en modo notificación.
    """
    token_configurado = bool(TELEGRAM_BOT_TOKEN and TELEGRAM_BOT_TOKEN != "your_telegram_bot_token_here")
    webhook_configurado = bool(TELEGRAM_WEBHOOK_URL and "your-backend" not in TELEGRAM_WEBHOOK_URL)
    chat_id_notificaciones = os.getenv("TELEGRAM_NOTIFICATIONS_CHAT_ID", "")
    chat_id_configurado = bool(chat_id_notificaciones and chat_id_notificaciones != "your_telegram_chat_id_here")

    return {
        "token_configurado": token_configurado,
        "webhook_configurado": webhook_configurado,
        "webhook_url": TELEGRAM_WEBHOOK_URL if webhook_configurado else "No configurado",
        "chat_id_configurado": chat_id_configurado,
        "chat_id_notificaciones": chat_id_notificaciones if chat_id_configurado else "No configurado",
        "listo": token_configurado and webhook_configurado and chat_id_configurado
    }

