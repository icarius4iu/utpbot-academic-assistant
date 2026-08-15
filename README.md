# UTP IA - Asistente Académico Virtual (v2.0.0)

Este proyecto es un sistema de asistencia académica e inteligencia artificial diseñado para la Universidad Tecnológica del Perú (UTP). Permite a estudiantes, docentes y administradores interactuar con un asistente inteligente capaz de procesar consultas sobre horarios, asistencias, notas, evaluaciones e información institucional oficial.

Esta versión **v2.0.0** introduce un panel de administración premium, hardening de seguridad avanzada, integración completa con un bot de Telegram y esquemas listos para despliegue en producción.

---

## 📚 Documentación Técnica

**Para llevar el proyecto a producción, lee estos documentos en orden:**

1. **[ARQUITECTURA.md](./ARQUITECTURA.md)** 🏗️  
   Diseño general del sistema, componentes, flujos de datos y diagrama de arquitectura.

2. **[STACK.md](./STACK.md)** 🛠️  
   Stack tecnológico completo: FastAPI, Python, JavaScript, Railway, Vercel, APIs externas.

3. **[DEPLOYMENT.md](./DEPLOYMENT.md)** 🚀  
   Guía paso-a-paso para desplegar en Railway (backend), Vercel (frontend) y activar Telegram webhook.

4. **[PRODUCCION.md](./PRODUCCION.md)** ✅  
   Checklist final de producción, configuración de seguridad, testing, monitoreo y runbooks de incident response.

---

## 🌟 Nuevas Características (v2.0.0)

### 📊 1. Panel de Administración (Dashboard Admin)
Un entorno de control dinámico y exclusivo para el rol de administrador (`admin.html`) con una estética oscura premium, glassmorphism y métricas en tiempo real utilizando **Chart.js**:
- **KPI Cards**: Total de consultas históricas, consultas del día de hoy, usuarios únicos activos y la categoría académica con mayor demanda.
- **Gráficos en Tiempo Real**:
  - *Actividad Diaria*: Historial visual de consultas durante los últimos 30 días.
  - *Distribución por Categorías*: Gráfico donut con el porcentaje de temas (horarios, notas, trámites, etc.).
  - *Consultas por Rol*: Gráfico de barras contrastando la actividad entre estudiantes y docentes.
- **Control de Acceso (Auth Guard)**: Rutas administrativas protegidas mediante JSON Web Tokens (JWT) y guards dedicados.
- **Log de Interacciones**: Tabla detallada con las últimas 20 consultas académicas, con buscador interactivo por texto en tiempo real.

### 🛡️ 2. Seguridad Avanzada y Hardening
- **JWT Guards Dedicados**: Control de acceso granular para estudiantes, docentes y administradores.
- **Rate Limiting**: Protección integrada contra ataques de denegación de servicio (DoS) y abusos de API mediante `slowapi` (200 consultas por hora / 30 por minuto por dirección IP).
- **Security Headers**: Inyección automática de cabeceras HTTP recomendadas por OWASP:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `X-XSS-Protection: 1; mode=block`
  - `Strict-Transport-Security` (HSTS) en entornos de producción.
- **CORS Restringido**: Orígenes permitidos controlados de forma estricta por el archivo de variables de entorno `.env`.

### 🤖 3. Integración con Telegram (Bot Oficial)
El asistente ahora es accesible de forma directa y asíncrona desde Telegram a través de un Bot automatizado vía **Webhooks**:
- **Comandos Soportados**:
  - `/start` — Inicialización e instrucciones de uso.
  - `/identificar` — Vincula el chat de Telegram a tu código institucional UTP.
  - `/ayuda` — Lista los comandos disponibles.
  - `/nueva` — Reinicia el hilo de conversación y el historial.
  - `/salir` — Cierra la sesión activa.
- **Procesamiento en Background**: Utiliza tareas en segundo plano de FastAPI para garantizar respuestas de webhook por debajo del límite estricto de 5 segundos de Telegram.

---

## 💻 Configuración Local (Desarrollo)

### Requisitos
- Python 3.11+
- Una cuenta de Google AI Studio (para `GEMINI_API_KEY`)
- Un Google Spreadsheet configurado (para `SPREADSHEET_ID`)
- Un bot de Telegram creado desde `@BotFather` (opcional para el bot)

### Paso 1: Configurar el Entorno del Backend
Crea un archivo `.env` en la carpeta `backend/` basándote en `backend/.env.example`:

```env
# Google Sheets
GOOGLE_CREDENTIALS_FILE=credentials.json
SPREADSHEET_ID=tu_spreadsheet_id

# Gemini API
GEMINI_API_KEY=tu_api_key_de_gemini
GEMINI_MODEL=gemini-2.5-flash

# JWT
JWT_SECRET_KEY=tu_clave_secreta_jwt_muy_segura
JWT_ALGORITHM=HS256
JWT_EXPIRATION_HOURS=8

# Admin Credentials
ADMIN_USERNAME=admin
ADMIN_PASSWORD=UTPAdmin2024Secure!

# Server & CORS
CORS_ORIGINS=http://localhost:5500,http://127.0.0.1:5500
DEBUG=true

# Telegram Bot
TELEGRAM_BOT_TOKEN=tu_token_de_telegram
TELEGRAM_WEBHOOK_URL=https://tu-backend-url.com/telegram/webhook
```

Instala las dependencias y activa el entorno virtual:
```bash
cd backend
python -m venv venv
source venv/bin/activate  # En Linux/macOS
# o bien en Windows:
# venv\Scripts\activate

pip install -r requirements.txt
```

### Paso 2: Ejecutar el Servidor Backend
```bash
uvicorn main:app --reload --port 8000
```
El backend estará disponible en: `http://localhost:8000`.

### Paso 3: Abrir el Frontend
1. Dirígete a la carpeta `frontend/`.
2. Asegúrate de que `script.js` y `admin.js` apunten de forma dinámica o manual a tu API local (`http://localhost:8000`).
3. Abre `index.html` utilizando `Live Server` en VS Code o cualquier servidor local estático.
4. Para entrar al panel de administración:
   - Ve a `admin.html` directamente, o inicia sesión desde `index.html` con tu usuario y contraseña de administrador definidos en el `.env`.

---

## 🚀 Guía de Despliegue en Producción

### 🌍 Despliegue del Frontend (Vercel)
El frontend se puede subir a **Vercel** de manera gratuita y muy rápida. El proyecto ya incluye un archivo `vercel.json` con reescrituras para SPA, headers de seguridad HTTP y control de indexación:
1. Instala el CLI de Vercel: `npm install -g vercel`
2. Ejecuta `vercel` en el directorio raíz del proyecto.
3. Elige la carpeta `frontend` como el directorio de salida estático o usa el configurador interactivo.

### 🔌 Despliegue del Backend (Railway o Render)
El backend FastAPI está preparado con un archivo `railway.toml` y un `Procfile` estándar para plataformas PaaS:
1. Conecta tu repositorio de GitHub a **Railway** (o **Render**).
2. Agrega las variables de entorno detalladas en tu `.env` dentro de la configuración de Railway.
3. Railway compilará el proyecto usando Nixpacks automáticamente.
4. Una vez desplegado, copia la URL pública de tu backend y agrégala a:
   - `CORS_ORIGINS` en el backend.
   - La constante `API_BASE` y `API_URL` en tus archivos JavaScript del frontend.

### 🤖 Activación del Bot de Telegram (Webhook)
Una vez que tu backend se encuentre desplegado públicamente (ej. en Railway):
1. Asegúrate de que la variable `TELEGRAM_WEBHOOK_URL` en Railway apunte a `https://tu-backend.railway.app/telegram/webhook`.
2. Inicia sesión en el **Panel de Administración** (`admin.html`) como administrador.
3. Ve a la sección **Bot Telegram** en la barra lateral.
4. Haz clic en **Activar Webhook** para registrar tu backend de forma automática ante los servidores de Telegram.

---

## 📂 Estructura del Repositorio

- `backend/`: Servidor FastAPI, enrutamiento, modelos Pydantic, y servicios (Gemini, Sheets, Telegram, Analytics).
- `frontend/`: Archivos del cliente (HTML, CSS, JS) incluyendo el Chat de usuario y el Panel de administración.
- `vercel.json`: Ajustes de ruteo y seguridad HTTP para el despliegue del frontend en Vercel.
- `railway.toml` / `Procfile`: Configuración para despliegue e inicio automatizado del backend en la nube.
- `UTP_Base_Conocimiento_2026.pdf`: Base oficial de información académica utilizada por la IA.
- `utp_info.txt`: Conversión en texto de la base académica para procesamiento y contextualización ágil.
- `UTPBot_Colab.ipynb`: Cuaderno heredado para ejecuciones rápidas y pruebas.

