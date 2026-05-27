"""
Modelos Pydantic para validación de datos del sistema UTPBot.
Define los esquemas de request/response para todos los endpoints.
"""

from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any


# ===================== AUTH =====================

class LoginRequest(BaseModel):
    """Esquema para la solicitud de inicio de sesión."""
    codigo: str = Field(..., description="Código institucional del usuario")
    password: str = Field(..., description="Contraseña del usuario")


class LoginResponse(BaseModel):
    """Esquema para la respuesta de inicio de sesión."""
    token: str
    nombre: str
    rol: str  # "estudiante", "docente" o "admin"
    idioma: str
    codigo: str


# ===================== CHAT =====================

class MensajeHistorial(BaseModel):
    """Esquema para un mensaje del historial de chat."""
    rol: str  # "user" o "assistant"
    contenido: str


class ChatRequest(BaseModel):
    """Esquema para la solicitud de chat con el bot."""
    codigo_usuario: str
    rol: str  # "estudiante" o "docente"
    mensaje: str
    historial: List[MensajeHistorial] = []
    idioma_preferido: Optional[str] = "es"
    file_name: Optional[str] = None
    file_mime: Optional[str] = None
    file_data: Optional[str] = None


class ChatResponse(BaseModel):
    """Esquema para la respuesta del chat."""
    respuesta: str
    sugerencias: List[str] = []


# ===================== ADMIN =====================

class UpdateSheetRequest(BaseModel):
    """Esquema para actualizar una celda en Google Sheets."""
    hoja: str = Field(..., description="Nombre de la hoja en el spreadsheet")
    fila_id: str = Field(..., description="Identificador de la fila (código del estudiante/docente)")
    columna: str = Field(..., description="Nombre de la columna a actualizar")
    nuevo_valor: str = Field(..., description="Nuevo valor para la celda")


class FAQItem(BaseModel):
    """Esquema para un ítem de FAQ analytics."""
    categoria: str
    cantidad: int
    preguntas_ejemplo: List[str] = []


class FAQAnalyticsResponse(BaseModel):
    """Esquema para la respuesta de analytics de FAQ."""
    total_consultas: int
    categorias: List[FAQItem]


# ===================== DASHBOARD ADMIN =====================

class OverviewStats(BaseModel):
    """Estadísticas generales del sistema."""
    total_consultas: int
    consultas_hoy: int
    usuarios_activos: int
    categoria_top: str
    porcentaje_estudiantes: float
    porcentaje_docentes: float


class DayStat(BaseModel):
    """Estadística de consultas por día."""
    fecha: str
    cantidad: int


class CategoryStat(BaseModel):
    """Estadística de consultas por categoría."""
    categoria: str
    cantidad: int
    porcentaje: float


class RoleStat(BaseModel):
    """Estadística de consultas por rol."""
    rol: str
    cantidad: int


class RecentLog(BaseModel):
    """Registro reciente de consulta."""
    fecha: str
    codigo_usuario: str
    rol: str
    pregunta: str
    categoria: str


class DashboardResponse(BaseModel):
    """Respuesta completa del dashboard de administración."""
    overview: OverviewStats
    por_dia: List[DayStat]
    por_categoria: List[CategoryStat]
    por_rol: List[RoleStat]
    recientes: List[RecentLog]


# ===================== DOCENTE =====================

class EstudianteSeccion(BaseModel):
    """Esquema para datos de un estudiante en una sección."""
    codigo: str
    nombre: str
    asistencia: Optional[dict] = None
    notas: Optional[dict] = None


class SeccionDocente(BaseModel):
    """Esquema para la información de una sección del docente."""
    curso: str
    seccion: str
    horario: str
    estudiantes: List[EstudianteSeccion] = []


# ===================== TELEGRAM =====================

class TelegramUpdate(BaseModel):
    """Esquema para un update de Telegram (webhook)."""
    update_id: int
    message: Optional[Dict[str, Any]] = None
    callback_query: Optional[Dict[str, Any]] = None
