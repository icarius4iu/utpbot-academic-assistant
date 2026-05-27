"""
Rutas de chat para el sistema UTPBot.
Maneja la comunicación con el chatbot de Gemini.
"""

from fastapi import APIRouter, Depends
from datetime import datetime, timezone
from models.schemas import ChatRequest, ChatResponse
from services.sheets_service import sheets_service
from services.gemini_service import gemini_service
from utils.prompt_builder import construir_prompt_sistema
from utils.jwt_utils import verificar_token

router = APIRouter(tags=["Chat"])


@router.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest, usuario: dict = Depends(verificar_token)):
    """
    Endpoint principal del chatbot.
    
    1. Lee los datos del usuario desde Google Sheets según su código y rol
    2. Construye un prompt dinámico con los datos del usuario
    3. Llama a la API de Gemini con el prompt y el historial
    4. Registra la pregunta en FAQ_Log para analytics
    5. Retorna la respuesta y 3 sugerencias de preguntas
    """
    codigo = request.codigo_usuario
    rol = request.rol
    mensaje = request.mensaje

    # 1. Recopilar datos del usuario según su rol
    if rol == "estudiante":
        datos_usuario = sheets_service.recopilar_datos_estudiante(codigo)
        nombre = datos_usuario.get("info_personal", {}).get("nombre", "Estudiante")
    else:
        datos_usuario = sheets_service.recopilar_datos_docente(codigo)
        nombre = datos_usuario.get("info_personal", {}).get("nombre", "Docente")

    # 3. Convertir historial al formato esperado
    historial = [
        {"rol": msg.rol, "contenido": msg.contenido}
        for msg in request.historial
    ]

    # Detectar si es el primer mensaje real del usuario (sin historial previo)
    es_primer_mensaje = len(historial) == 0

    # 2. Construir el prompt del sistema con datos inyectados
    system_prompt = construir_prompt_sistema(
        rol=rol,
        nombre=nombre,
        datos_usuario=datos_usuario,
        idioma=request.idioma_preferido,
        es_primer_mensaje=es_primer_mensaje
    )

    # 4. Generar respuesta con Gemini
    respuesta, sugerencias = gemini_service.generar_respuesta(
        system_prompt=system_prompt,
        mensaje_usuario=mensaje,
        historial=historial,
        datos_usuario=datos_usuario,
        file_name=request.file_name,
        file_mime=request.file_mime,
        file_data=request.file_data
    )

    # 5. Categorizar y registrar la pregunta para analytics
    categoria = gemini_service.categorizar_pregunta(mensaje)
    fecha_actual = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    sheets_service.registrar_faq(
        fecha=fecha_actual,
        codigo_usuario=codigo,
        rol=rol,
        pregunta=mensaje,
        categoria=categoria
    )

    # 6. Retornar respuesta con sugerencias
    return ChatResponse(
        respuesta=respuesta,
        sugerencias=sugerencias
    )
