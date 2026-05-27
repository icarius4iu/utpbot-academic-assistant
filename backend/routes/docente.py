"""
Rutas del docente para el sistema UTPBot.
Maneja la consulta de secciones, alumnos, asistencia y notas.
"""

from fastapi import APIRouter, Depends, HTTPException
from services.sheets_service import sheets_service
from utils.jwt_utils import verificar_token

router = APIRouter(prefix="/docente", tags=["Docente"])


@router.get("/seccion/{codigo_docente}")
async def obtener_secciones_docente(
    codigo_docente: str,
    usuario: dict = Depends(verificar_token)
):
    """
    Retorna la lista de alumnos, asistencia y notas de todas las secciones del docente.
    Solo accesible para el docente dueño de las secciones.
    """
    # Verificar que el usuario sea docente
    if usuario.get("rol") != "docente":
        raise HTTPException(
            status_code=403,
            detail="Acceso denegado. Solo los docentes pueden acceder a esta información."
        )

    # Verificar que el docente solo acceda a sus propias secciones
    if usuario.get("codigo") != codigo_docente:
        raise HTTPException(
            status_code=403,
            detail="Solo puedes consultar tus propias secciones."
        )

    try:
        secciones = sheets_service.obtener_datos_estudiantes_seccion(codigo_docente)

        if not secciones:
            return {
                "codigo_docente": codigo_docente,
                "secciones": [],
                "message": "No se encontraron secciones asignadas para este docente."
            }

        return {
            "codigo_docente": codigo_docente,
            "total_secciones": len(secciones),
            "secciones": secciones
        }

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error al obtener las secciones: {str(e)}"
        )


@router.get("/resumen/{codigo_docente}")
async def obtener_resumen_docente(
    codigo_docente: str,
    usuario: dict = Depends(verificar_token)
):
    """
    Retorna un resumen general del docente: cursos, total de alumnos, etc.
    """
    if usuario.get("rol") != "docente":
        raise HTTPException(status_code=403, detail="Acceso denegado.")

    if usuario.get("codigo") != codigo_docente:
        raise HTTPException(status_code=403, detail="Solo puedes consultar tu propio resumen.")

    try:
        docente = sheets_service.buscar_docente(codigo_docente)
        if not docente:
            raise HTTPException(status_code=404, detail="Docente no encontrado.")

        secciones = sheets_service.obtener_secciones_docente(codigo_docente)
        total_alumnos = 0
        cursos = []

        for seccion in secciones:
            cursos.append(seccion.get("curso", ""))
            try:
                import json
                lista = seccion.get("lista_estudiantes", "[]")
                if isinstance(lista, str):
                    estudiantes = json.loads(lista)
                else:
                    estudiantes = lista
                total_alumnos += len(estudiantes)
            except Exception:
                pass

        return {
            "codigo_docente": codigo_docente,
            "nombre": docente.get("nombre", ""),
            "departamento": docente.get("departamento", ""),
            "total_secciones": len(secciones),
            "total_alumnos": total_alumnos,
            "cursos": list(set(cursos))
        }

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error: {str(e)}")
