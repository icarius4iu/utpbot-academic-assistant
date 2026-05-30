"""
Ruta de transcripción de audio para el modo de voz del UTPBot.
Recibe un fragmento de audio en base64 desde el frontend y usa Gemini para transcribirlo.
"""

import base64
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from utils.jwt_utils import verificar_token
from google import genai
from google.genai import types
import os

router = APIRouter(tags=["Voz"])

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")


class TranscribeRequest(BaseModel):
    audio_base64: str          # Audio grabado en el navegador, codificado en base64
    mime_type: str = "audio/webm"  # Formato del audio (webm, mp4, ogg, etc.)


class TranscribeResponse(BaseModel):
    texto: str                 # Transcripción del audio
    confianza: float = 1.0


@router.post("/transcribe", response_model=TranscribeResponse)
async def transcribe_audio(
    request: TranscribeRequest,
    usuario: dict = Depends(verificar_token)
):
    """
    Transcribe audio usando Gemini Multimodal.
    El audio viene como base64 desde el navegador (grabado con MediaRecorder).
    No depende de los servidores de Google Speech disponibles en la red del usuario.
    """
    if not GEMINI_API_KEY:
        raise HTTPException(status_code=503, detail="API de IA no configurada.")

    try:
        # Decodificar el audio de base64
        audio_bytes = base64.b64decode(request.audio_base64)

        if len(audio_bytes) < 1000:
            raise HTTPException(status_code=400, detail="Audio muy corto o vacío.")

        client = genai.Client(api_key=GEMINI_API_KEY)

        # Construir el prompt de transcripción
        prompt = (
            "Eres un transcriptor preciso. Transcribe exactamente lo que dice la persona "
            "en el siguiente audio. Responde SOLO con el texto transcrito, sin explicaciones, "
            "sin comillas y sin signos adicionales. Si el audio está en español, transcribe en español. "
            "Si no se escucha nada o hay demasiado ruido, responde únicamente con: [SILENCIO]"
        )

        # Usar Gemini con el audio
        response = client.models.generate_content(
            model="gemini-1.5-flash",
            contents=[
                types.Part.from_bytes(
                    data=audio_bytes,
                    mime_type=request.mime_type
                ),
                prompt
            ]
        )

        texto = response.text.strip() if response.text else "[SILENCIO]"

        # Si es silencio, devolver vacío para que el frontend lo maneje
        if texto.upper() in ("[SILENCIO]", "SILENCIO", "[SILENCE]", "SILENCE"):
            texto = ""

        return TranscribeResponse(texto=texto)

    except HTTPException:
        raise
    except Exception as e:
        print(f"❌ Error en transcripción: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"Error al transcribir el audio: {str(e)}"
        )
