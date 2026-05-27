"""
Rutas de autenticación para el sistema UTPBot.
Maneja el login de estudiantes, docentes y administradores.
"""

import os
from fastapi import APIRouter, HTTPException
from models.schemas import LoginRequest, LoginResponse
from services.sheets_service import sheets_service
from utils.jwt_utils import crear_token

router = APIRouter(prefix="/auth", tags=["Autenticación"])

# =====================================================================
# ADMIN — Credenciales desde variables de entorno (nunca hardcodeadas)
# =====================================================================
ADMIN_USERNAME = os.getenv("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "")
DEBUG_MODE = os.getenv("DEBUG", "false").lower() == "true"

# =====================================================================
# USUARIOS DEMO — Funcionan en modo DEBUG, incluso sin Google Sheets.
# En producción (DEBUG=false) solo funcionan credenciales reales.
# =====================================================================
DEMO_ESTUDIANTES = {
    "E001": {
        "codigo": "E001", "nombre": "Ana García López",
        "carrera": "Ingeniería de Sistemas", "ciclo": "5",
        "idioma_preferido": "es"
    },
    "E002": {
        "codigo": "E002", "nombre": "Carlos Mendoza Ríos",
        "carrera": "Administración de Empresas", "ciclo": "3",
        "idioma_preferido": "es"
    },
    "E003": {
        "codigo": "E003", "nombre": "Lucía Torres Vásquez",
        "carrera": "Psicología", "ciclo": "7",
        "idioma_preferido": "en"
    },
}

DEMO_DOCENTES = {
    "D001": {
        "codigo": "D001", "nombre": "Dr. Roberto Flores",
        "departamento": "Ingeniería",
        "cursos_asignados": "Algoritmos, Estructura de Datos",
        "idioma_preferido": "es"
    },
    "D002": {
        "codigo": "D002", "nombre": "Mg. Carmen Salinas",
        "departamento": "Ciencias Básicas",
        "cursos_asignados": "Cálculo I, Estadística",
        "idioma_preferido": "es"
    },
}


def _buscar_usuario(codigo: str):
    """
    Busca un usuario primero en Google Sheets y, si falla o no existe,
    cae al listado demo local (solo en modo DEBUG).
    Retorna (datos_dict, rol) o (None, None).
    """
    # 1️⃣ Intentar Google Sheets
    try:
        est = sheets_service.buscar_estudiante(codigo)
        if est:
            return est, "estudiante"
        doc = sheets_service.buscar_docente(codigo)
        if doc:
            return doc, "docente"
    except Exception as e:
        print(f"⚠️  Google Sheets no disponible: {e}")

    # 2️⃣ Fallback a datos demo (solo en modo DEBUG)
    if DEBUG_MODE:
        if codigo in DEMO_ESTUDIANTES:
            return DEMO_ESTUDIANTES[codigo], "estudiante"
        if codigo in DEMO_DOCENTES:
            return DEMO_DOCENTES[codigo], "docente"

    return None, None


@router.post("/login", response_model=LoginResponse)
async def login(request: LoginRequest):
    """
    Endpoint de inicio de sesión.

    - Admin: usa credenciales del .env (ADMIN_USERNAME / ADMIN_PASSWORD)
    - Docentes/Estudiantes: valida contra Google Sheets (fallback demo en DEBUG)
    """
    codigo = request.codigo.strip()
    password = request.password.strip()

    # ── Admin especial ──────────────────────────────────────────────
    if codigo == ADMIN_USERNAME:
        if not ADMIN_PASSWORD:
            raise HTTPException(
                status_code=503,
                detail="Panel de administración no configurado. Contacte al administrador del sistema."
            )
        if password != ADMIN_PASSWORD:
            raise HTTPException(status_code=401, detail="Credenciales de administrador incorrectas.")

        token = crear_token(
            codigo="ADMIN",
            nombre="Administrador UTP",
            rol="admin",
            idioma="es"
        )
        return LoginResponse(
            token=token,
            nombre="Administrador UTP",
            rol="admin",
            idioma="es",
            codigo="ADMIN"
        )

    # ── Estudiantes / Docentes ───────────────────────────────────────
    datos, rol = _buscar_usuario(codigo)

    if datos is None:
        msg = "Código institucional no encontrado."
        if DEBUG_MODE:
            msg += " Prueba con E001, E002, E003 (estudiantes) o D001, D002 (docentes)."
        raise HTTPException(status_code=404, detail=msg)

    # Validar contraseña (= código por defecto en demo; en producción usar hash)
    if password != str(codigo):
        raise HTTPException(status_code=401, detail="Contraseña incorrecta.")

    idioma = datos.get("idioma_preferido", "es")
    nombre = datos.get("nombre", rol.capitalize())

    token = crear_token(codigo=codigo, nombre=nombre, rol=rol, idioma=idioma)

    return LoginResponse(
        token=token,
        nombre=nombre,
        rol=rol,
        idioma=idioma,
        codigo=codigo
    )
