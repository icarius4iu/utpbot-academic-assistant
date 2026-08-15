# Golden Fixtures — Paridad Python vs. Quarkus

Toolkit para comparar las respuestas HTTP del backend Python original contra el
backend Quarkus nuevo, endpoint por endpoint. Resultados de la última corrida real:
ver `backend-quarkus/PARITY_REPORT.md` (15 de 18 casos idénticos byte a byte).

## Requisitos

```bash
pip install requests
```

## Uso

```bash
# 1. Levantar el backend Python (modo DEBUG=true usa datos demo, no necesita
#    Google Sheets real -- ver backend-quarkus/PARITY_REPORT.md para el detalle)
cd backend && JWT_SECRET_KEY=x ADMIN_USERNAME=admin ADMIN_PASSWORD=x DEBUG=true \
  uvicorn main:app --port 8000

# 2. Levantar el backend Quarkus (necesita el emulador de Firebase Auth si no hay
#    proyecto real -- ver backend-quarkus/API_TESTING.md)
npx firebase-tools emulators:start --only auth --project cualquier-nombre &
export FIREBASE_AUTH_EMULATOR_HOST=localhost:9099
cd backend-quarkus && mvn quarkus:dev

# 3. Capturar fixtures de cada uno
python etl/golden/capture.py --backend python --base-url http://localhost:8000 \
  --admin-password 'TuAdminPassword' --out /tmp/fixtures/python

python etl/golden/capture.py --backend quarkus --base-url http://localhost:8080 \
  --admin-password 'TuAdminPassword' --out /tmp/fixtures/quarkus

# 4. Comparar
python etl/golden/compare.py /tmp/fixtures/python /tmp/fixtures/quarkus
```

`compare.py` termina con código de salida 0 si todo coincide, 1 si hay diferencias
(útil para correrlo en CI antes de un corte de producción).

## Qué NO cubre este toolkit

`/chat`, `/transcribe` y el flujo de agendado (`agendar_tiempo_estudio`) son
no-determinísticos (dependen de Gemini) y no se pueden diffear byte a byte —
requieren revisión cualitativa manual. Ver la lista de casos de prueba sugeridos en
`backend-quarkus/API_TESTING.md`.
