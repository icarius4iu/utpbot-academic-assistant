"""
UTPBot - Asistente Académico Virtual de la UTP
Punto de entrada principal del backend FastAPI.

Para ejecutar:
    uvicorn main:app --reload --port 8000
"""

import os
import time
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from dotenv import load_dotenv

# Cargar variables de entorno
load_dotenv()

# Configurar logging
logging.basicConfig(
    level=logging.INFO if os.getenv("DEBUG", "false").lower() == "true" else logging.WARNING,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s"
)
logger = logging.getLogger(__name__)

# Importar las rutas
from routes.auth import router as auth_router
from routes.chat import router as chat_router
from routes.admin import router as admin_router
from routes.docente import router as docente_router
from routes.telegram import router as telegram_router

# ===================== RATE LIMITER =====================

limiter = Limiter(key_func=get_remote_address, default_limits=["200/hour", "30/minute"])


# ===================== LIFESPAN =====================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Eventos de inicio y cierre de la aplicación."""
    logger.info("🚀 UTPBot API iniciando...")
    yield
    logger.info("🛑 UTPBot API detenida.")


# ===================== CONFIGURACIÓN DE LA APLICACIÓN =====================

app = FastAPI(
    title="UTPBot API",
    description=(
        "API del Asistente Académico Virtual de la Universidad Tecnológica del Perú (UTP). "
        "Proporciona endpoints para autenticación, chat con IA, administración académica "
        "e integración con Telegram."
    ),
    version="2.0.0",
    docs_url="/docs" if os.getenv("DEBUG", "false").lower() == "true" else None,
    redoc_url="/redoc" if os.getenv("DEBUG", "false").lower() == "true" else None,
    lifespan=lifespan
)

# Registrar rate limiter
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)


# ===================== MIDDLEWARE DE SEGURIDAD =====================

@app.middleware("http")
async def security_headers_middleware(request: Request, call_next):
    """Agrega cabeceras de seguridad HTTP a todas las respuestas."""
    start_time = time.time()
    response: Response = await call_next(request)

    # Security Headers
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["X-XSS-Protection"] = "1; mode=block"
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    response.headers["Permissions-Policy"] = "camera=(), geolocation=()"
    response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate"

    # Solo HTTPS en producción
    if os.getenv("DEBUG", "false").lower() != "true":
        response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"

    # Tiempo de respuesta para monitoreo
    process_time = time.time() - start_time
    response.headers["X-Process-Time"] = str(round(process_time * 1000, 2)) + "ms"

    return response


# ===================== CONFIGURACIÓN DE CORS =====================

cors_origins_str = os.getenv(
    "CORS_ORIGINS",
    "http://localhost:5500,http://127.0.0.1:5500,http://localhost:3000"
)
cors_origins = [origin.strip() for origin in cors_origins_str.split(",") if origin.strip()]

# En modo DEBUG agregar orígenes locales adicionales
if os.getenv("DEBUG", "false").lower() == "true":
    cors_origins.extend([
        "http://localhost:3000",
        "http://localhost:8080",
        "http://127.0.0.1:5500",
        "http://localhost:5500",
        "http://127.0.0.1:5501",
        "http://localhost:5501",
        "null",  # Para archivos HTML abiertos directamente en desarrollo
    ])

cors_origins = list(set(cors_origins))

# Permitir todos los orígenes de forma segura ya que la autenticación es por cabecera Bearer Token (JWT) y no usa cookies.
# Esto previene bloqueos de CORS en cualquier dominio de Vercel (tanto producción como previews temporales).
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ===================== REGISTRAR RUTAS =====================

app.include_router(auth_router)
app.include_router(chat_router)
app.include_router(admin_router)
app.include_router(docente_router)
app.include_router(telegram_router)


# ===================== RUTA RAÍZ =====================

@app.get("/", tags=["General"])
async def root():
    """Endpoint raíz que confirma que la API está funcionando."""
    return {
        "nombre": "UTPBot API",
        "version": "2.0.0",
        "estado": "✅ Activo",
        "descripcion": "Asistente Académico Virtual de la UTP",
        "documentacion": "/docs" if os.getenv("DEBUG", "false").lower() == "true" else "No disponible en producción"
    }


@app.get("/health", tags=["General"])
async def health_check():
    """Endpoint de health check para verificar el estado del servidor."""
    return {"status": "ok", "service": "utpbot-api", "version": "2.0.0"}


# ===================== EJECUCIÓN DIRECTA =====================

if __name__ == "__main__":
    import uvicorn
    debug = os.getenv("DEBUG", "false").lower() == "true"
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=debug,
        log_level="info" if debug else "warning"
    )
