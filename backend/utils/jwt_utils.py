"""
Utilidades JWT para autenticación del sistema UTPBot.
Maneja la creación y verificación de tokens JWT.
Incluye guards para roles: estudiante, docente, admin.
"""

import jwt
import os
from datetime import datetime, timedelta, timezone
from dotenv import load_dotenv
from fastapi import HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

load_dotenv()

# Configuración JWT desde variables de entorno
SECRET_KEY = os.getenv("JWT_SECRET_KEY", "utpbot_secret_key_default_2024")
ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
EXPIRATION_HOURS = int(os.getenv("JWT_EXPIRATION_HOURS", "8"))

# Esquema de seguridad para FastAPI (Bearer token)
security_scheme = HTTPBearer()


def crear_token(codigo: str, nombre: str, rol: str, idioma: str) -> str:
    """
    Crea un token JWT con los datos del usuario.

    Args:
        codigo: Código institucional del usuario
        nombre: Nombre completo del usuario
        rol: Rol del usuario ('estudiante', 'docente' o 'admin')
        idioma: Idioma preferido del usuario

    Returns:
        Token JWT como string
    """
    payload = {
        "codigo": codigo,
        "nombre": nombre,
        "rol": rol,
        "idioma": idioma,
        "exp": datetime.now(timezone.utc) + timedelta(hours=EXPIRATION_HOURS),
        "iat": datetime.now(timezone.utc)
    }
    token = jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)
    return token


def _decodificar_token(credentials: HTTPAuthorizationCredentials) -> dict:
    """
    Decodifica y valida un token JWT. Función interna reutilizable.

    Raises:
        HTTPException 401 si el token es inválido o ha expirado
    """
    token = credentials.credentials
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=401,
            detail="Token expirado. Inicia sesión nuevamente.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except jwt.InvalidTokenError:
        raise HTTPException(
            status_code=401,
            detail="Token inválido.",
            headers={"WWW-Authenticate": "Bearer"},
        )


def verificar_token(credentials: HTTPAuthorizationCredentials = Security(security_scheme)) -> dict:
    """
    Verifica y decodifica un token JWT desde el header Authorization.
    Permite cualquier rol válido (estudiante, docente, admin).

    Returns:
        Diccionario con los datos del payload del token
    """
    return _decodificar_token(credentials)


def verificar_admin(credentials: HTTPAuthorizationCredentials = Security(security_scheme)) -> dict:
    """
    Verifica que el token JWT pertenezca a un usuario con rol 'admin'.

    Returns:
        Diccionario con los datos del payload del token

    Raises:
        HTTPException 403 si el rol no es admin
    """
    payload = _decodificar_token(credentials)
    if payload.get("rol") != "admin":
        raise HTTPException(
            status_code=403,
            detail="Acceso denegado. Se requiere rol de administrador.",
        )
    return payload


def verificar_docente_o_admin(credentials: HTTPAuthorizationCredentials = Security(security_scheme)) -> dict:
    """
    Verifica que el token JWT pertenezca a un docente o administrador.

    Returns:
        Diccionario con los datos del payload del token

    Raises:
        HTTPException 403 si el rol no es docente ni admin
    """
    payload = _decodificar_token(credentials)
    if payload.get("rol") not in ("docente", "admin"):
        raise HTTPException(
            status_code=403,
            detail="Acceso denegado. Se requiere rol de docente o administrador.",
        )
    return payload
