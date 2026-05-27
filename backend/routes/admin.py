"""
Rutas de administración para el sistema UTPBot.
Acceso exclusivo para el rol 'admin'.
Incluye dashboard de métricas y gestión de datos.
"""

from fastapi import APIRouter, Depends, HTTPException
from models.schemas import UpdateSheetRequest
from services.sheets_service import sheets_service
from services.analytics_service import analytics_service
from utils.jwt_utils import verificar_admin

router = APIRouter(prefix="/admin", tags=["Administración"])


# ─────────────────────────────────────────────────────────────────────
#  DASHBOARD — Métricas completas en una sola llamada
# ─────────────────────────────────────────────────────────────────────

@router.get("/dashboard")
async def obtener_dashboard(usuario: dict = Depends(verificar_admin)):
    """
    Retorna todas las métricas del dashboard en una sola respuesta.
    - Overview (totales, hoy, usuarios activos, categoría top)
    - Consultas por día (últimos 30 días)
    - Distribución por categoría
    - Distribución por rol (estudiante / docente)
    - Últimas 20 consultas
    """
    return analytics_service.obtener_dashboard_completo()


# ─────────────────────────────────────────────────────────────────────
#  ENDPOINTS INDIVIDUALES (granularidad fina)
# ─────────────────────────────────────────────────────────────────────

@router.get("/stats/overview")
async def obtener_overview(usuario: dict = Depends(verificar_admin)):
    """Retorna las métricas generales del sistema (KPIs principales)."""
    return analytics_service.obtener_overview()


@router.get("/stats/by-day")
async def obtener_por_dia(dias: int = 30, usuario: dict = Depends(verificar_admin)):
    """Retorna el número de consultas por día para los últimos N días."""
    return analytics_service.obtener_stats_por_dia(dias=dias)


@router.get("/stats/by-category")
async def obtener_por_categoria(usuario: dict = Depends(verificar_admin)):
    """Retorna la distribución de consultas por categoría."""
    return analytics_service.obtener_stats_por_categoria()


@router.get("/stats/by-role")
async def obtener_por_rol(usuario: dict = Depends(verificar_admin)):
    """Retorna la distribución de consultas por rol de usuario."""
    return analytics_service.obtener_stats_por_rol()


@router.get("/recent-logs")
async def obtener_logs_recientes(
    limite: int = 20,
    usuario: dict = Depends(verificar_admin)
):
    """Retorna las últimas N consultas con todos sus detalles."""
    return analytics_service.obtener_preguntas_recientes(limite)


# ─────────────────────────────────────────────────────────────────────
#  GESTIÓN DE DATOS (Google Sheets)
# ─────────────────────────────────────────────────────────────────────

@router.get("/faq-analytics")
async def obtener_faq_analytics(usuario: dict = Depends(verificar_admin)):
    """Retorna las preguntas más frecuentes agrupadas por categoría."""
    return analytics_service.obtener_faq_analytics()


@router.post("/update-sheet")
async def actualizar_sheet(
    request: UpdateSheetRequest,
    usuario: dict = Depends(verificar_admin)
):
    """
    Permite al administrador actualizar una celda específica de Google Sheets.
    """
    try:
        resultado = sheets_service.actualizar_celda(
            nombre_hoja=request.hoja,
            fila_id=request.fila_id,
            columna=request.columna,
            nuevo_valor=request.nuevo_valor
        )

        if resultado:
            return {
                "success": True,
                "message": f"Celda actualizada correctamente en '{request.hoja}'."
            }
        else:
            raise HTTPException(
                status_code=404,
                detail=f"No se encontró la fila con ID '{request.fila_id}' en la hoja '{request.hoja}'."
            )

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error al actualizar: {str(e)}")
