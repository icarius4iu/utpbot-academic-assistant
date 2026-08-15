# 🚀 Guía de Despliegue - UTPBot v2.0.0

## 📋 Tabla de Contenidos
1. [Pre-Requisitos](#pre-requisitos)
2. [Configuración Local](#configuración-local)
3. [Despliegue Backend (Railway)](#despliegue-backend-railway)
4. [Despliegue Frontend (Vercel)](#despliegue-frontend-vercel)
5. [Configuración Telegram Webhook](#configuración-telegram-webhook)
6. [Verificación Post-Deploy](#verificación-post-deploy)
7. [Troubleshooting](#troubleshooting)

---

## Pre-Requisitos

### Cuentas Necesarias

- [ ] GitHub account (para repositorio)
- [ ] Google Cloud account (para Gemini + Sheets)
- [ ] Railway account (para backend)
- [ ] Vercel account (para frontend)
- [ ] Telegram account (para bot)

### Configuración Google Cloud

#### 1. Crear Google Cloud Project

```bash
# 1. Ir a https://console.cloud.google.com
# 2. Crear nuevo proyecto: "utpbot-production"
# 3. Habilitar APIs:
#    - Google Gemini API
#    - Google Sheets API
#    - Google Calendar API (opcional)
```

#### 2. Generar Service Account

```bash
# En Google Cloud Console:
# 1. Ir a "Service Accounts"
# 2. Crear nuevo service account: "utpbot-api"
# 3. Agregar roles:
#    - Editor (o solo Sheets + Gemini)
# 4. Crear JSON key
# 5. Descargar archivo .json
```

#### 3. Compartir Google Spreadsheet

```bash
# 1. Crear/editar tu Google Spreadsheet académico
# 2. Compartir con el email del service account:
#    utpbot-api@utpbot-production.iam.gserviceaccount.com
# 3. Dar acceso "Editor"
# 4. Copiar el SPREADSHEET_ID de la URL:
#    https://docs.google.com/spreadsheets/d/{SPREADSHEET_ID}/edit
```

### Generar Secretos

```bash
# JWT Secret Key (64+ caracteres hex)
python -c "import secrets; print(secrets.token_hex(32))"
# Output: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z...

# Admin Password (25+ caracteres)
# Generar manualmente: Combinar mayúsculas, minúsculas, números, símbolos
# Ejemplo: SecureAdmin@2024!#$%Prod
```

### Telegram Bot

```bash
# 1. Abrir Telegram y buscar: @BotFather
# 2. Enviar comando: /newbot
# 3. Seguir pasos para crear bot:
#    - Nombre: "UTPBot Academic"
#    - Username: "utpbot_academic"
# 4. Copiar el TOKEN que BotFather proporciona
# 5. Guardar: TELEGRAM_BOT_TOKEN
```

---

## Configuración Local

### 1. Clonar Repositorio

```bash
git clone https://github.com/TU_USERNAME/utpbot-academic-assistant.git
cd utpbot-academic-assistant
```

### 2. Configurar Backend

```bash
cd backend

# Crear archivo .env
cat > .env << EOF
# === Google Services ===
GOOGLE_CREDENTIALS_FILE=credentials.json
SPREADSHEET_ID=tu_spreadsheet_id_aqui

# === Gemini API ===
GEMINI_API_KEY=tu_gemini_api_key_aqui
GEMINI_MODEL=gemini-2.5-flash

# === JWT ===
JWT_SECRET_KEY=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z...
JWT_ALGORITHM=HS256
JWT_EXPIRATION_HOURS=8

# === Admin ===
ADMIN_USERNAME=admin
ADMIN_PASSWORD=SecureAdmin@2024!#$%Prod

# === Server ===
CORS_ORIGINS=http://localhost:5500,http://127.0.0.1:5500
DEBUG=true

# === Telegram ===
TELEGRAM_BOT_TOKEN=tu_telegram_token_aqui
TELEGRAM_WEBHOOK_URL=https://tu-backend-railway.app/telegram/webhook

# === Google Calendar (opcional) ===
GOOGLE_CALENDAR_ID=primary

# === Telegram Notifications (opcional) ===
TELEGRAM_NOTIFICATIONS_CHAT_ID=tu_chat_id_aqui
EOF

# Copiar Google Credentials
# Descargar el JSON de Google Cloud y:
cp ~/Downloads/utpbot-production-xxx.json credentials.json

# Crear virtual environment
python -m venv venv
source venv/bin/activate  # Linux/macOS
# o en Windows:
# venv\Scripts\activate

# Instalar dependencias
pip install -r requirements.txt

# Verificar instalación
python -c "import fastapi; print(f'FastAPI {fastapi.__version__} OK')"
```

### 3. Ejecutar Backend Localmente

```bash
# Desde backend/:
uvicorn main:app --reload --port 8000

# Debe mostrar:
# INFO:     Uvicorn running on http://127.0.0.1:8000
# INFO:     Application startup complete

# Verificar API:
# - Swagger: http://localhost:8000/docs
# - Health: http://localhost:8000/health
```

### 4. Configurar Frontend

```bash
cd ../frontend

# Editar script.js - actualizar API_BASE:
# Buscar: const API_BASE = 
# Cambiar a: const API_BASE = 'http://localhost:8000'

# Abrir en navegador:
# Opción 1: Live Server (VS Code)
# Opción 2: http-server
#   npm install -g http-server
#   http-server . -p 5500

# O simplemente con Python:
python -m http.server 5500
```

### 5. Verificar Funcionamiento Local

```bash
# 1. Abrir http://localhost:5500 en navegador
# 2. Click en botón de login
# 3. Entrar: username=admin, password=(del .env)
# 4. Debe mostrar: "Login OK" + acceso al chat
# 5. Escribir un mensaje en el chat
# 6. Debe responder (puede demorar 1-2s por Gemini)
# 7. Ir a http://localhost:5500/admin.html
# 8. Debe mostrar dashboard con gráficos
```

---

## Despliegue Backend (Railway)

### Paso 1: Conectar GitHub a Railway

```bash
# 1. Ir a https://railway.app
# 2. Sign in con GitHub
# 3. Nuevo proyecto: "New Project"
# 4. "Deploy from GitHub repo"
# 5. Seleccionar: utpbot-academic-assistant
# 6. Hacer click "Deploy"
```

### Paso 2: Agregar Variables de Entorno

```bash
# En Railway Dashboard:
# 1. Ir a proyecto → Variables
# 2. Agregar cada variable:

# Google Services
GOOGLE_CREDENTIALS_FILE=credentials.json
SPREADSHEET_ID=tu_id_aqui

# Gemini
GEMINI_API_KEY=tu_api_key_aqui
GEMINI_MODEL=gemini-2.5-flash

# JWT (IMPORTANTE: generar nuevo para producción)
JWT_SECRET_KEY=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z...
JWT_ALGORITHM=HS256
JWT_EXPIRATION_HOURS=8

# Admin Credentials (cambiar para producción)
ADMIN_USERNAME=admin
ADMIN_PASSWORD=SecureAdmin@2024!#$%Prod

# CORS (actualizar con dominio Vercel)
CORS_ORIGINS=https://tu-app.vercel.app,https://app.utp.edu.pe

# Debug
DEBUG=false

# Telegram
TELEGRAM_BOT_TOKEN=tu_token_aqui
TELEGRAM_WEBHOOK_URL=https://tu-backend-railway.app/telegram/webhook

# Google Calendar
GOOGLE_CALENDAR_ID=primary
```

### Paso 3: Subir Google Credentials

```bash
# Opción A: Raw file
# 1. En Railway Variables
# 2. Crear variable: GOOGLE_CREDENTIALS_JSON
# 3. Pegar el contenido completo del .json

# Opción B: Base64 (recomendado)
cat credentials.json | base64 > credentials.b64
# Copiar contenido de credentials.b64 a variable GOOGLE_CREDENTIALS_B64
# Agregar startup script para decodificar
```

### Paso 4: Configuración Railway.toml

```toml
# Ya incluido en el repo:
[build]
builder = "NIXPACKS"

[build.env]
PYTHON_VERSION = "3.11"

[deploy]
startCommand = "cd backend && uvicorn main:app --host 0.0.0.0 --port $PORT"
healthcheckPath = "/health"
healthcheckTimeout = 30
restartPolicyType = "ON_FAILURE"
restartPolicyMaxRetries = 3
```

### Paso 5: Deploy y Obtener URL

```bash
# Railway automáticamente:
# 1. Clona el repo
# 2. Instala Python 3.11 (Nixpacks)
# 3. Corre: pip install -r requirements.txt
# 4. Inicia: uvicorn main:app --host 0.0.0.0 --port $PORT

# Esperar ~2-3 minutos

# Una vez deployed:
# 1. Ir a Railway Dashboard
# 2. Copiar la URL pública:
#    https://utpbot-api-prod.railway.app

# Verificar:
curl https://utpbot-api-prod.railway.app/health
# Debe retornar: {"status":"ok","service":"utpbot-api","version":"2.0.0"}
```

### Paso 6: Configurar Dominio Personalizado (Opcional)

```bash
# Si tienes dominio: api.utp.edu.pe

# En Railway:
# 1. Proyecto → Settings → Domains
# 2. Agregar dominio: api.utp.edu.pe
# 3. Railway proporciona: CNAME record
# 4. En tu proveedor DNS:
#    CNAME api.utp.edu.pe → railway-prod.up.railway.app
# 5. Esperar ~30 min para propagación
# 6. Verificar con: nslookup api.utp.edu.pe
```

---

## Despliegue Frontend (Vercel)

### Paso 1: Conectar GitHub a Vercel

```bash
# 1. Ir a https://vercel.com
# 2. Sign in con GitHub
# 3. Click "Import Project"
# 4. Seleccionar repo: utpbot-academic-assistant
# 5. Framework: "Other"
# 6. Build settings:
#    - Build command: (leave empty)
#    - Output directory: frontend
```

### Paso 2: Configurar API URL

```bash
# Crear archivo: frontend/.env.local

VITE_API_URL=https://utpbot-api-prod.railway.app

# O editar script.js directamente:
# Buscar: const API_BASE = 'http://localhost:8000'
# Reemplazar: const API_BASE = 'https://utpbot-api-prod.railway.app'
```

### Paso 3: Agregar Secrets (si necesario)

```bash
# En Vercel Dashboard:
# Proyecto → Settings → Environment Variables
# (Normalmente no se necesitan para frontend estático)
```

### Paso 4: Deploy

```bash
# Vercel automáticamente:
# 1. Clona el repo
# 2. Build: (ninguno, frontend es estático)
# 3. Deploy: archivos del folder frontend/ a CDN

# Esperar ~1-2 minutos

# URL pública:
# https://utpbot.vercel.app (o tu dominio personalizado)
```

### Paso 5: Verificar Deploy

```bash
# 1. Abrir https://utpbot.vercel.app
# 2. Debe cargar el chat interface
# 3. Click login (admin, password)
# 4. Debe conectar a backend en Railway
# 5. Probar enviar mensaje
```

### Paso 6: Configurar Dominio Personalizado

```bash
# Si tienes: app.utp.edu.pe

# En Vercel:
# 1. Proyecto → Settings → Domains
# 2. Agregar: app.utp.edu.pe
# 3. Copiar nameservers
# 4. En tu registrador de dominio:
#    - Cambiar nameservers a los de Vercel
#    - O agregar CNAME si es subdominio
# 5. Esperar ~30 min para propagación
```

### Paso 7: Actualizar CORS en Backend

```bash
# Ahora que tienes la URL de Vercel:

# En Railway Dashboard:
# Variables → CORS_ORIGINS
# Cambiar: http://localhost:5500
# Por:     https://utpbot.vercel.app

# Si tienes dominio personalizado:
# CORS_ORIGINS=https://app.utp.edu.pe
```

---

## Configuración Telegram Webhook

### Paso 1: Obtener Backend URL

```bash
# De Railway: https://utpbot-api-prod.railway.app
# (Ya configurada en variable TELEGRAM_WEBHOOK_URL)
```

### Paso 2: Activar Webhook desde Admin Panel

```bash
# 1. Ir a admin.html
# 2. Login con admin credentials
# 3. En sidebar: "Telegram Bot"
# 4. Click: "Activar Webhook"
# 5. Sistema hace POST a Telegram:
#    POST https://api.telegram.org/bot{TOKEN}/setWebhook
#    body: { url: "https://tu-backend/telegram/webhook" }
```

### Paso 3: Verificar Webhook Activo

```bash
# En backend logs (Railway):
# Debe mostrar: "Telegram webhook set successfully"

# Verificar con curl:
curl "https://api.telegram.org/bot{TOKEN}/getWebhookInfo"

# Output:
# {
#   "ok": true,
#   "result": {
#     "url": "https://tu-backend/telegram/webhook",
#     "has_custom_certificate": false,
#     "pending_update_count": 0
#   }
# }
```

### Paso 4: Probar Bot

```bash
# En Telegram:
# 1. Buscar tu bot: @utpbot_academic (o el username que creaste)
# 2. Enviar: /start
# 3. Debe responder con mensaje de bienvenida
# 4. Enviar: /ayuda
# 5. Debe mostrar lista de comandos

# Comandos disponibles:
# /start        - Bienvenida
# /identificar  - Vincular código UTP
# /ayuda        - Lista de comandos
# /nueva        - Nueva conversación
# /salir        - Logout
```

---

## Verificación Post-Deploy

### Checklist de Verificación

#### Backend (Railway)

- [ ] URL pública accesible
- [ ] Health check responde: `GET /health` → 200 OK
- [ ] JWT endpoints funcionan: `POST /auth/login` → 200 OK
- [ ] Chat funciona: `POST /chat/send` → 200 OK
- [ ] Admin analytics carga: `GET /admin/analytics` → 200 OK
- [ ] Rate limiting activo: después de 30 requests/min → 429
- [ ] Logs visible en Railway dashboard
- [ ] No errors en startup

#### Frontend (Vercel)

- [ ] URL pública carga
- [ ] Chat interface renderiza
- [ ] Login funciona
- [ ] Mensajes se envían al backend
- [ ] Admin dashboard carga (admin.html)
- [ ] Gráficos se renderizan (Chart.js)
- [ ] Dark mode funciona
- [ ] Responsivo en mobile

#### Integration

- [ ] Chat end-to-end funciona
- [ ] Respuestas de Gemini IA llegan
- [ ] Analytics se guardan en Sheets
- [ ] Telegram bot responde a mensajes
- [ ] Admin puede ver logs y métricas

### Performance Baselines

```bash
# Verificar con curl:

# 1. API response time
time curl https://backend.railway.app/health
# Debe ser < 100ms

# 2. Chat latency (incluye Gemini)
time curl -X POST https://backend.railway.app/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"message":"hola","session_id":"test"}'
# Debe ser < 3000ms (incluye Gemini latency)

# 3. Frontend load
# Chrome DevTools → Network tab
# Initial load: < 2s
# Time to interactive: < 3s
```

---

## Troubleshooting

### Backend (Railway)

#### "502 Bad Gateway"

```
Causa: Backend no está respondiendo
Solución:
1. Verificar logs en Railway: "Logs" tab
2. Ver si hay error de Python
3. Verificar variables de entorno (GEMINI_API_KEY, etc)
4. Reiniciar deployment: Railway → Redeploy
```

#### "401 Unauthorized"

```
Causa: JWT inválido o expirado
Solución:
1. Verificar JWT_SECRET_KEY en .env
2. Verificar que token tiene 8 horas (no expirado)
3. Verificar header: "Authorization: Bearer <token>"
4. Clear localStorage en frontend
```

#### "429 Too Many Requests"

```
Causa: Rate limiting activado
Solución:
1. Normal: esperar 1 minuto
2. Si persiste: verificar slowapi config en main.py
3. Aumentar límite si es necesario (RECOMENDADO: DEJAR COMO ESTÁ)
```

#### "CORS Error"

```
Causa: Origen no permitido
Solución:
1. Verificar CORS_ORIGINS en .env incluye tu dominio
2. Verificar URL en frontend es exacta (sin trailing slash)
3. Ejemplo correcto: https://app.vercel.app (sin /api)
```

#### Gemini API Error

```
Causa: API key inválida o cuota excedida
Solución:
1. Verificar GEMINI_API_KEY en Railway variables
2. Verificar en Google Cloud que Gemini API está habilitada
3. Verificar cuota en Google Cloud Console
4. Si cuota excedida: actualizar plan o esperar reset
```

### Frontend (Vercel)

#### "Cannot connect to backend"

```
Causa: API_BASE URL incorrecta o backend no responde
Solución:
1. Abrir DevTools → Network tab
2. Verificar que POST /chat/send va a URL correcta
3. Verificar CORS_ORIGINS en backend incluye el dominio
4. Backend debe retornar Access-Control-Allow-Origin header
```

#### "Dark mode not working"

```
Causa: CSS media query no detecta preferencia
Solución:
1. Verificar navegador soporta prefers-color-scheme
2. Settings → Appearance → Dark mode (OS level)
3. O usar toggle en página (theme button)
```

#### "Chart.js no renderiza"

```
Causa: librería no cargó o datos inválidos
Solución:
1. Verificar CDN: https://cdn.jsdelivr.net/npm/chart.js
2. Verificar /admin/chart-data retorna datos válidos
3. Abrir DevTools → Console para ver errores
```

### Telegram Bot

#### "Webhook not working"

```
Causa: URL no es pública o HTTPS inválido
Solución:
1. Verificar URL es HTTPS (no HTTP)
2. Certificado SSL válido
3. Respuesta debe ser < 5 segundos
4. Estado en Telegram: getWebhookInfo → pending_update_count = 0
```

#### "Bot no responde"

```
Causa: Token inválido o webhook no registrado
Solución:
1. Verificar TELEGRAM_BOT_TOKEN en .env
2. Verificar webhook está activo: getWebhookInfo
3. Ver logs en Railway para errores
4. Reimplementar webhook: setWebhook endpoint
```

### Google Sheets

#### "Connection refused"

```
Causa: Credenciales inválidas o spreadsheet no compartido
Solución:
1. Verificar GOOGLE_CREDENTIALS_FILE es válido JSON
2. Verificar spreadsheet compartido con service account
3. Verificar SPREADSHEET_ID es correcto
4. Ver logs: "gspread.exceptions.SpreadsheetNotFound"
```

---

## Rollback & Revert

### Si algo sale mal

```bash
# En Railway:
# 1. Ir a Deployments tab
# 2. Ver historial de deploys
# 3. Click en deployment anterior
# 4. Click "Redeploy"
# 5. Esperar mientras se revierte

# En Vercel:
# 1. Ir a Deployments tab
# 2. Click en deployment anterior
# 3. Click "Promote to Production"
```

### Git Rollback

```bash
# Si necesitas revertir código:
git log --oneline | head -10
# Encuentra commit anterior

git revert <commit-hash>
# o
git reset --hard <commit-hash>

git push origin main
# Railway y Vercel auto-redeploy
```

---

## Monitoreo Continuo

### Checking Backend Health

```bash
# Script de monitoreo (cron job):
#!/bin/bash

BACKEND_URL="https://utpbot-api-prod.railway.app/health"
EXPECTED_STATUS="ok"

RESPONSE=$(curl -s $BACKEND_URL)

if echo $RESPONSE | grep -q $EXPECTED_STATUS; then
  echo "✅ Backend healthy"
else
  echo "❌ Backend down"
  # Enviar alerta (email, Slack, etc)
fi
```

### Railway Monitoring

```
Dashboard → Monitoring
- CPU usage
- Memory usage
- Request count
- Error rate
- Latency p50/p95/p99
```

### Vercel Analytics

```
Dashboard → Analytics
- Page views
- Unique visitors
- Page load time
- Web Vitals (LCP, FID, CLS)
```

---

**Guía Completa de Despliegue - UTPBot v2.0.0**  
*Última actualización: Agosto 2026*
