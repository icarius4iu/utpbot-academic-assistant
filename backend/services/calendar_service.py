"""
Servicio de Google Calendar para el sistema UTPBot.
Permite agendar eventos (sesiones de estudio) en el calendario del usuario
usando las credenciales de la cuenta de servicio de Google.
"""

import os
import json
import httpx
import logging
from google.oauth2.service_account import Credentials
import google.auth.transport.requests

logger = logging.getLogger(__name__)

# Ámbito requerido para escribir en Google Calendar
SCOPES = ["https://www.googleapis.com/auth/calendar"]


class CalendarService:
    """
    Servicio que gestiona la creación de eventos en Google Calendar.
    Utiliza gspread-compatible o variables de entorno JSON para las credenciales.
    """

    def __init__(self):
        self._creds = None

    def _obtener_credenciales(self) -> Credentials:
        """Carga las credenciales de la cuenta de servicio de Google."""
        if self._creds is not None:
            return self._creds

        # 1. Intentar desde variable de entorno JSON (Producción / Railway)
        creds_json = os.getenv("GOOGLE_CREDENTIALS_JSON", "")
        if creds_json:
            try:
                info = json.loads(creds_json)
                self._creds = Credentials.from_service_account_info(info, scopes=SCOPES)
                logger.info("[OK] Credenciales de Calendar cargadas desde GOOGLE_CREDENTIALS_JSON.")
                return self._creds
            except Exception as e:
                logger.error(f"Error al cargar credenciales desde GOOGLE_CREDENTIALS_JSON: {e}")

        # 2. Intentar desde archivo físico local (Desarrollo)
        creds_file = os.getenv("GOOGLE_CREDENTIALS_FILE", "credentials.json")
        actual_creds_file = creds_file
        
        # Buscar en el directorio raíz o en backend/
        if not os.path.exists(actual_creds_file) and os.path.exists(os.path.join("backend", creds_file)):
            actual_creds_file = os.path.join("backend", creds_file)

        if not os.path.exists(actual_creds_file):
            raise FileNotFoundError(
                f"No se encontró el archivo de credenciales de Google: {creds_file} "
                "ni la variable de entorno GOOGLE_CREDENTIALS_JSON."
            )

        self._creds = Credentials.from_service_account_file(actual_creds_file, scopes=SCOPES)
        logger.info(f"[OK] Credenciales de Calendar cargadas desde archivo: {actual_creds_file}")
        return self._creds

    async def obtener_token_acceso(self) -> str:
        """Obtiene y refresca de forma síncrona el token OAuth2 de Google."""
        creds = self._obtener_credenciales()
        auth_req = google.auth.transport.requests.Request()
        
        # Refrescar token (operación síncrona pero extremadamente rápida en local)
        creds.refresh(auth_req)
        return creds.token

    async def crear_evento_estudio(
        self,
        titulo: str,
        fecha_inicio: str,  # Formato ISO 8601, ej: "2026-06-03T16:00:00"
        fecha_fin: str,     # Formato ISO 8601, ej: "2026-06-03T18:00:00"
        descripcion: str = ""
    ) -> dict:
        """
        Agrega un evento en el Google Calendar especificado por GOOGLE_CALENDAR_ID.
        """
        calendar_id = os.getenv("GOOGLE_CALENDAR_ID", "primary")
        
        try:
            token = await self.obtener_token_acceso()
        except Exception as e:
            logger.error(f"Error de autenticación con Google Calendar: {e}")
            raise Exception("No se pudo autenticar con los servidores de Google Calendar.")

        # Forzar zona horaria local de Perú (-05:00) si no viene configurada en la cadena
        if "T" in fecha_inicio and not (fecha_inicio.endswith("Z") or "-" in fecha_inicio[10:] or "+" in fecha_inicio[10:]):
            fecha_inicio += "-05:00"
        if "T" in fecha_fin and not (fecha_fin.endswith("Z") or "-" in fecha_fin[10:] or "+" in fecha_fin[10:]):
            fecha_fin += "-05:00"

        event_body = {
            "summary": titulo,
            "description": descripcion,
            "start": {
                "dateTime": fecha_inicio,
                "timeZone": "America/Lima"
            },
            "end": {
                "dateTime": fecha_fin,
                "timeZone": "America/Lima"
            },
            "reminders": {
                "useDefault": True
            }
        }

        url = f"https://www.googleapis.com/calendar/v3/calendars/{calendar_id}/events"
        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }

        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(url, json=event_body, headers=headers)
            
            if resp.status_code != 200:
                try:
                    resp_json = resp.json()
                    error_msg = resp_json.get("error", {}).get("message", "Error desconocido")
                except Exception:
                    error_msg = resp.text
                
                logger.error(f"Google Calendar API retornó estado {resp.status_code}: {error_msg}")
                raise Exception(f"Google Calendar API Error: {error_msg}")
            
            logger.info(f"✅ Evento '{titulo}' creado exitosamente en el calendario '{calendar_id}'")
            return resp.json()


# Instancia única del servicio
calendar_service = CalendarService()
