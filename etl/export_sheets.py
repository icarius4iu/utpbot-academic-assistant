"""
ETL paso 1/2: Exporta las 12 hojas de Google Sheets de producción a JSON.

Reutiliza el MISMO patrón de credenciales que backend/services/sheets_service.py
(_conectar): primero intenta GOOGLE_CREDENTIALS_JSON (contenido del JSON de la
service account como variable de entorno, patrón usado en Railway), y si no
existe cae a un archivo local credentials.json.

NO exporta la hoja "FAQ_Log" — por decisión explícita del usuario, el dashboard
de analytics arranca en cero el día del corte (ver plan de migración, sección
"Migración de datos (ETL)").

Uso:
    cd etl
    pip install -r requirements.txt
    export SPREADSHEET_ID=...            # el mismo que usa el backend Python hoy
    export GOOGLE_CREDENTIALS_JSON='...'  # o dejar credentials.json en este directorio
    python export_sheets.py

Salida: etl/data/<hoja>.json (uno por cada una de las 12 hojas).
"""

import json
import os
import sys

import gspread
from google.oauth2.service_account import Credentials

SCOPES = [
    "https://www.googleapis.com/auth/spreadsheets",
    "https://www.googleapis.com/auth/drive",
]

# Las 12 hojas de origen — deliberadamente NO incluye "FAQ_Log" (ver docstring).
HOJAS = [
    "Estudiantes",
    "Docentes",
    "Horarios",
    "Notas",
    "Cursos",
    "Examenes",
    "Asistencia",
    "Avances_Trabajo",
    "Proyectos_Finales",
    "Calendario",
    "Secciones_Docente",
]

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")


def _conectar():
    """Idéntico en espíritu a SheetsService._conectar() del backend Python."""
    spreadsheet_id = os.getenv("SPREADSHEET_ID")
    if not spreadsheet_id:
        sys.exit("ERROR: falta la variable de entorno SPREADSHEET_ID.")

    creds_json = os.getenv("GOOGLE_CREDENTIALS_JSON")
    if creds_json:
        info = json.loads(creds_json)
        creds = Credentials.from_service_account_info(info, scopes=SCOPES)
    else:
        ruta = os.path.join(os.path.dirname(__file__), "credentials.json")
        if not os.path.exists(ruta):
            ruta = os.path.join(os.path.dirname(__file__), "..", "backend", "credentials.json")
        if not os.path.exists(ruta):
            sys.exit(
                "ERROR: no se encontraron credenciales. Define GOOGLE_CREDENTIALS_JSON "
                "o coloca credentials.json en etl/ o backend/."
            )
        creds = Credentials.from_service_account_file(ruta, scopes=SCOPES)

    cliente = gspread.authorize(creds)
    return cliente.open_by_key(spreadsheet_id)


def main():
    os.makedirs(DATA_DIR, exist_ok=True)
    spreadsheet = _conectar()

    resumen = {}
    for nombre_hoja in HOJAS:
        try:
            hoja = spreadsheet.worksheet(nombre_hoja)
        except gspread.exceptions.WorksheetNotFound:
            print(f"[AVISO] Hoja '{nombre_hoja}' no encontrada en el spreadsheet — se omite.")
            resumen[nombre_hoja] = None
            continue

        registros = hoja.get_all_records()
        destino = os.path.join(DATA_DIR, f"{nombre_hoja}.json")
        with open(destino, "w", encoding="utf-8") as f:
            json.dump(registros, f, ensure_ascii=False, indent=2)

        resumen[nombre_hoja] = len(registros)
        print(f"[OK] {nombre_hoja}: {len(registros)} filas -> {destino}")

    print("\n=== Resumen de exportación ===")
    for hoja, n in resumen.items():
        estado = f"{n} filas" if n is not None else "NO ENCONTRADA"
        print(f"  {hoja:<20} {estado}")

    total_faltantes = sum(1 for n in resumen.values() if n is None)
    if total_faltantes:
        print(f"\n[AVISO] {total_faltantes} hoja(s) no encontradas. Revisa el spreadsheet antes de continuar.")
        sys.exit(1)

    print("\nExportación completa. Siguiente paso: python transform_and_load.py")


if __name__ == "__main__":
    main()
