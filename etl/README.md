# ETL — Migración de datos Sheets → PostgreSQL

Toolkit de migración de una sola vez (no se despliega en el jar de Quarkus). Ver el
plan completo en `/home/codespace/.claude/plans/bright-doodling-twilight.md`, sección
"Migración de datos (ETL)".

## Requisitos

```bash
cd etl
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
```

## Paso 1 — Exportar desde Google Sheets

Reutiliza las mismas credenciales que ya usa `backend/services/sheets_service.py` hoy
(la misma cuenta de servicio, el mismo `SPREADSHEET_ID`).

```bash
export SPREADSHEET_ID=<el mismo que usa el backend Python en producción>
export GOOGLE_CREDENTIALS_JSON='<contenido del JSON de la cuenta de servicio>'
# — o bien, coloca credentials.json en este directorio —
python export_sheets.py
```

Esto crea `etl/data/<Hoja>.json` para cada una de las 12 hojas de origen. **No** exporta
`FAQ_Log` — el dashboard de analytics arranca en cero (decisión del usuario).

## Paso 2 — Validar en seco (recomendado antes de tocar producción)

```bash
export DATABASE_URL=postgresql://usuario:password@host:5432/basededatos
python transform_and_load.py --dry-run
```

Reporta filas huérfanas (referencian un `codigo_estudiante`/`codigo_docente` que no
existe en `Estudiantes`/`Docentes`) sin escribir nada. Revisa el reporte antes de
continuar — Sheets no tenía integridad referencial, Postgres sí la va a exigir.

## Paso 3 — Cargar de verdad

```bash
python transform_and_load.py
```

Semántica **truncate-and-reload**: vacía las 13 tablas (en orden hijo→padre) y las
vuelve a poblar dentro de una única transacción — si algo falla a mitad de camino, se
hace rollback completo y no queda ningún dato a medias. Al final imprime un reporte de
verificación (conteo esperado vs. real por tabla).

Las contraseñas se siembran como `bcrypt(codigo)` — mismo comportamiento de login que
hoy ("tu contraseña es tu código"), pero ahora hasheada en vez de comparada en texto
plano (ver plan, sección "Autenticación").

## Flujo recomendado para el corte

1. Correr los 3 pasos contra un Postgres de **staging** primero.
2. Usar esa base de staging para las pruebas de paridad (golden-files, ver `etl/golden/`).
3. Repetir los 3 pasos contra el Postgres de **producción** en el momento real del corte,
   para que el snapshot esté lo más fresco posible.
4. Conservar `etl/data/*.json` como respaldo del snapshot usado en cada corte.
