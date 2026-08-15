# Despliegue — Backend Quarkus (Google Cloud Functions, 2nd gen)

Guía de despliegue del backend Quarkus a producción como una **Cloud Function HTTP
(2nd gen)** — toda la API REST existente (16 endpoints, sin reestructurar nada) corre
como una sola función gracias a la extensión `quarkus-google-cloud-functions-http`.
Ver el plan de migración original en
`/home/codespace/.claude/plans/bright-doodling-twilight.md` (esa versión asumía
Railway; este documento la reemplaza para el nuevo destino de despliegue).

**Decisiones ya tomadas** (ver el hilo de la conversación para el detalle de cada trade-off):
- **Gen 2** (no Gen 1) — corre sobre Cloud Run por debajo, mejor concurrencia y límites.
- **Modo JVM, sin `--min-instances`** — se acepta cold-start ocasional (primera
  consulta tras un rato inactivo tarda algunos segundos más: Firebase Admin SDK +
  Hibernate arrancando) a cambio de simplicidad y de no arriesgar un build nativo con
  el Admin SDK de Firebase (issues de GraalVM documentados).
- **Rate limiter tal cual está** (en memoria, por instancia) — con varias instancias
  concurrentes bajo carga, el límite de 200/hora + 30/min deja de ser estrictamente
  global, pero es una degradación aceptada para el tráfico esperado, no un blocker.

---

## 1. Prerrequisitos

```bash
# CLI de Google Cloud (si no lo tenés)
curl https://sdk.cloud.google.com | bash
gcloud init
gcloud auth login

# APIs necesarias en el proyecto GCP que uses para el backend
# (puede ser el mismo proyecto Firebase, ej. utpbot-staging, o uno separado)
gcloud config set project utpbot-staging
gcloud services enable cloudfunctions.googleapis.com \
  cloudbuild.googleapis.com \
  run.googleapis.com \
  artifactregistry.googleapis.com
```

---

## 2. Variables de entorno

Cloud Functions con muchas variables (y una que es un JSON completo con comas —
`GOOGLE_APPLICATION_CREDENTIALS_JSON`) se gestionan mejor con un archivo YAML que con
`--set-env-vars` inline (ese flag separa por comas y el JSON de la credencial choca
con esa sintaxis).

Crear `backend-quarkus/env.yaml` (**no commitear este archivo — agregarlo a
`.gitignore`**, ya contiene secretos reales):

```yaml
DATABASE_URL: "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres"
DATABASE_USER: "postgres.<tu-project-ref>"
DATABASE_PASSWORD: "<tu password de Supabase>"

FIREBASE_PROJECT_ID: "utpbot-staging"
GOOGLE_APPLICATION_CREDENTIALS_JSON: '{"type":"service_account",...}'

GEMINI_API_KEY: "<tu api key real>"
GEMINI_MODEL: "gemini-2.5-pro"

ADMIN_USERNAME: "admin"
ADMIN_PASSWORD_HASH: "<bcrypt hash>"

TELEGRAM_BOT_TOKEN: "<token real>"
TELEGRAM_WEBHOOK_URL: "https://<region>-<project>.cloudfunctions.net/utpbot-backend/telegram/webhook"
TELEGRAM_NOTIFICATIONS_CHAT_ID: "<chat id>"

GOOGLE_CALENDAR_ID: "primary"
DEBUG: "false"
```

> **Nota sobre Supabase e IPv6**: el host directo (`db.<ref>.supabase.co`) resuelve
> SOLO a una IP v6. Si el entorno de build/runtime de Cloud Functions no tiene salida
> IPv6 (confirmado que este sandbox de desarrollo no la tiene; verificar el caso real
> de Cloud Functions antes de asumir), usar el **connection pooler** de Supabase en su
> lugar — host `aws-0-<region>.pooler.supabase.com`, puerto `5432` (session mode,
> compatible con Hibernate/prepared statements), usuario con el formato
> `postgres.<project-ref>` (no solo `postgres`). Ya verificado funcionando end-to-end
> contra un proyecto Supabase real durante el desarrollo — ver el esquema ya aplicado.

---

## 3. Compilar y desplegar

```bash
cd backend-quarkus
mvn clean package -DskipTests
# Genera target/deployment/backend-quarkus-1.0.0-SNAPSHOT-runner.jar (uber-jar, ~125MB)

gcloud functions deploy utpbot-backend \
  --gen2 \
  --runtime=java21 \
  --region=us-east1 \
  --source=target/deployment \
  --entry-point=io.quarkus.gcp.functions.http.QuarkusHttpFunction \
  --trigger-http \
  --allow-unauthenticated \
  --memory=1Gi \
  --timeout=60s \
  --env-vars-file=env.yaml
```

`--allow-unauthenticated` es necesario porque la autenticación real la hace el propio
backend (JWT/Firebase ID tokens vía `Authorization: Bearer`, ver
`security/FirebaseAuthMechanism.java`) — si Cloud Functions exige además su propia
capa de IAM, `/telegram/webhook` (que Telegram llama sin credenciales de GCP) dejaría
de funcionar.

`--memory=1Gi` es un punto de partida razonable (Hibernate + Firebase Admin SDK +
Gemini SDK cargados en memoria); ajustar según lo que muestren los logs de Cloud
Functions tras las primeras invocaciones reales.

Al terminar, `gcloud` imprime la URL pública de la función
(`https://<region>-<project>.cloudfunctions.net/utpbot-backend` o, en Gen 2, a veces
un dominio `run.app`) — **todas las rutas cuelgan de esa misma URL base**
(`<url>/auth/login`, `<url>/chat`, etc.), porque `QuarkusHttpFunction` reenvía el
path completo al router REST existente sin prefijo adicional.

---

## 4. Verificar el despliegue

```bash
curl https://<tu-url-de-función>/health
# {"status":"ok","service":"utpbot-api","version":"3.0.0"}

# Ver logs en vivo:
gcloud functions logs read utpbot-backend --gen2 --region=us-east1 --limit=50
```

La primera migración de Flyway corre sola al arrancar la función (mismo comportamiento
verificado en desarrollo contra Supabase real) — no hace falta correr nada a mano.

---

## 5. Cortar el frontend

Mismos 2 puntos que en cualquier otro destino de despliegue:
1. `frontend/script.js` / `frontend/admin.js` → apuntar `API_URL`/`API_BASE` a la URL
   de la Cloud Function.
2. `frontend/firebase-config.js` → ya tiene los valores reales de `utpbot-staging`
   cargados.

## 6. Actualizar el webhook de Telegram

Una vez que `TELEGRAM_WEBHOOK_URL` en `env.yaml` apunte a la URL real de la función
(no se sabe hasta después del primer deploy — puede requerir un segundo
`gcloud functions deploy` para actualizar esa variable con la URL definitiva), activar
el webhook desde el panel admin (`/telegram/setup-webhook`) o directamente:
```bash
curl -X POST "https://<tu-url-de-función>/telegram/setup-webhook"
```

---

## Redeploy

Cualquier cambio de código: repetir el paso 3 completo (`mvn clean package` +
`gcloud functions deploy`, mismo comando, sin flags nuevos). Cambios de env vars sin
tocar código: mismo comando `gcloud functions deploy` (vuelve a leer `env.yaml`).
