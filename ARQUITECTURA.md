# 🏗️ Arquitectura del Sistema - UTPBot v2.0.0

## 📋 Tabla de Contenidos
1. [Resumen Ejecutivo](#resumen-ejecutivo)
2. [Diagrama General](#diagrama-general)
3. [Componentes Principales](#componentes-principales)
4. [Flujos de Datos](#flujos-de-datos)
5. [Estructura del Repositorio](#estructura-del-repositorio)

---

## Resumen Ejecutivo

**UTPBot** es un asistente académico inteligente con arquitectura **modular de tres capas**:

```
┌─────────────────────────────────────────────────────┐
│  PRESENTACIÓN (Frontend - Vercel)                    │
│  HTML5/CSS3 + JavaScript Vanilla                    │
└────────────────────┬────────────────────────────────┘
                     │ HTTPS/CORS
                     ↓
┌─────────────────────────────────────────────────────┐
│  LÓGICA DE NEGOCIOS (Backend - Railway)              │
│  FastAPI + Python 3.11 + Middleware de Seguridad   │
└────────────────────┬────────────────────────────────┘
                     │
        ┌────────────┼────────────┬────────────┐
        ↓            ↓            ↓            ↓
    ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
    │Gemini  │  │Sheets  │  │Telegram│  │Calendar│
    │  API   │  │  API   │  │  API   │  │  API   │
    └────────┘  └────────┘  └────────┘  └────────┘
```

---

## Diagrama General

### Vista de Componentes

```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND (Vercel)                         │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐      ┌──────────────────┐            │
│  │  Chat Interface  │      │  Admin Dashboard │            │
│  │  (index.html)    │      │  (admin.html)    │            │
│  ├──────────────────┤      ├──────────────────┤            │
│  │ • Messages list  │      │ • KPI cards      │            │
│  │ • Input form     │      │ • Charts (Chart.js)          │
│  │ • Microphone     │      │ • Logs table     │            │
│  │ • Auth modal     │      │ • Bot controls   │            │
│  └────────┬─────────┘      └────────┬─────────┘            │
│           │ script.js               │ admin.js             │
│           └───────────────┬─────────┘                       │
└───────────────────────────┼─────────────────────────────────┘
                            │ POST/GET HTTPS
                            │ JWT Bearer Token
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   BACKEND API (Railway)                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  FastAPI Application (main.py)                              │
│  ├─ CORS Middleware       → Controla orígenes              │
│  ├─ Security Headers      → OWASP hardening                │
│  ├─ Rate Limiter          → 200/hora, 30/min               │
│  └─ Exception Handlers    → Error responses                │
│                                                               │
│  Routes:                                                     │
│  ├─ /auth/*               → Autenticación JWT              │
│  ├─ /chat/*               → Chat principal                 │
│  ├─ /admin/*              → Analytics & logs               │
│  ├─ /docente/*            → Gestión docentes               │
│  ├─ /telegram/webhook     → Bot webhook                    │
│  └─ /transcribe           → Speech-to-text                 │
│                                                               │
│  Business Logic Layer:                                       │
│  ├─ services/gemini_service.py     → IA responses          │
│  ├─ services/sheets_service.py     → Data CRUD             │
│  ├─ services/telegram_service.py   → Bot logic             │
│  ├─ services/analytics_service.py  → Metrics               │
│  └─ services/calendar_service.py   → Scheduling            │
│                                                               │
└────────┬──────────┬──────────────┬────────────┬─────────────┘
         │          │              │            │
    HTTP │ gRPC     │ REST         │ Webhooks   │
         ↓          ↓              ↓            ↓
    ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
    │ Google │  │ Google │  │Telegram│  │ Google │
    │Gemini  │  │Sheets  │  │  Bot   │  │Calendar│
    └────────┘  └────────┘  └────────┘  └────────┘
```

### Flujo de Autenticación

```
Usuario Escribe
    ↓
POST /auth/login (credentials)
    ↓
┌─────────────────────────┐
│ Validar Admin User      │
│ (ADMIN_USERNAME/PASS)   │
└────────────┬────────────┘
             ↓ (si válido)
┌─────────────────────────┐
│ Generar JWT Token       │
│ Payload: {user, role}   │
│ Secret: JWT_SECRET_KEY  │
│ Exp: 8 horas           │
└────────────┬────────────┘
             ↓
Frontend guarda en localStorage
    ↓
Próximas requests:
  Header: Authorization: Bearer <token>
    ↓
┌─────────────────────────┐
│ Middleware verifica JWT │
│ Valida signature        │
│ Verifica expiración     │
└────────────┬────────────┘
             ↓ (si válido)
    Procesa request
```

### Flujo de Chat

```
Usuario: "¿Cuál es mi horario?"
    ↓
Frontend POST /chat/send { message, session_id }
    ↓
┌──────────────────────────────────────┐
│ 1. Auth Middleware                   │
│    ✓ Valida JWT token               │
│    ✓ Verifica expiración            │
└──────────────┬───────────────────────┘
               ↓
┌──────────────────────────────────────┐
│ 2. Rate Limiter                      │
│    ✓ Verifica límite por IP (30/min)│
│    ✓ Rechaza si excedido (429)      │
└──────────────┬───────────────────────┘
               ↓
┌──────────────────────────────────────┐
│ 3. Route Handler (routes/chat.py)   │
│    ✓ Extrae user_id del JWT        │
│    ✓ Valida inputs                 │
└──────────────┬───────────────────────┘
               ↓
        ┌──────┴──────┐
        ↓             ↓
    ┌─────────────┐  ┌──────────────┐
    │ Recuperar   │  │ Preparar     │
    │ contexto    │  │ prompt IA    │
    │ académico   │  │ (UTP Base)   │
    │ (Sheets)    │  └──────┬───────┘
    └──────┬──────┘         │
           └────────┬───────┘
                    ↓
         ┌────────────────────┐
         │ Gemini Service     │
         │ - llamar API       │
         │ - procesar stream  │
         │ - formatear respuesta
         └────────┬───────────┘
                  ↓
         ┌────────────────────┐
         │ Analytics Service  │
         │ - guardar log      │
         │ - actualizar métricas
         └────────┬───────────┘
                  ↓
    Retorna: { response, metadata }
                  ↓
Frontend recibe JSON
    ↓
Renderiza en chat interface
    ↓
Usuario ve: "Tu horario es: Lunes 8am - 10am..."
```

---

## Componentes Principales

### Backend

#### 1. **main.py - Punto de Entrada**

```python
# Responsabilidades:
- Crear app FastAPI
- Registrar middlewares de seguridad
- Registrar rutas
- Configurar CORS
- Configurar rate limiter
- Manejar startup/shutdown
```

**Middlewares:**
- `CORSMiddleware` → Controla orígenes permitidos
- `SecurityHeadersMiddleware` → OWASP headers
- `RateLimitMiddleware` → slowapi 30/min por IP

#### 2. **routes/auth.py - Autenticación**

```
POST /auth/login
├─ Input: { username, password }
├─ Valida contra ADMIN_USERNAME, ADMIN_PASSWORD
└─ Output: { access_token, token_type, expires_in }

POST /auth/logout
├─ Input: Bearer token
└─ Output: { message: "Logged out" }

POST /auth/verify
├─ Input: Bearer token
└─ Output: { valid, user, expires }
```

#### 3. **routes/chat.py - Chat Principal**

```
POST /chat/send
├─ Input: { message, session_id }
├─ Recupera contexto académico (sheets_service)
├─ Llama Gemini IA (gemini_service)
├─ Guarda log (analytics_service)
└─ Output: { response, metadata, timestamp }

GET /chat/history?session_id=XXX
├─ Input: session_id, limit, offset
└─ Output: [ { role, content, timestamp }, ... ]

POST /chat/clear-history
├─ Input: session_id
└─ Output: { message: "History cleared" }
```

#### 4. **routes/admin.py - Administración**

```
GET /admin/analytics
├─ Requiere: Admin role
└─ Output: { 
    total_queries,
    today_queries,
    active_users,
    top_category 
  }

GET /admin/logs?limit=20&offset=0
├─ Requiere: Admin role
└─ Output: [ {
    timestamp,
    user_id,
    query,
    response,
    category,
    role
  }, ... ]

GET /admin/chart-data
├─ Requiere: Admin role
└─ Output: {
    daily_activity: [ { date, count }, ... ],
    categories: [ { name, percentage }, ... ],
    by_role: { estudiante, docente }
  }
```

#### 5. **routes/telegram.py - Bot Webhook**

```
POST /telegram/webhook
├─ Recibe: Update JSON de Telegram
├─ Procesa: Background task (< 5s respuesta)
├─ Comandos:
│  ├─ /start
│  ├─ /identificar
│  ├─ /ayuda
│  ├─ /nueva
│  └─ /salir
└─ Retorna: 200 OK inmediato

GET /telegram/activate-webhook
├─ Requiere: Admin role
├─ Acción: Registra webhook en Telegram
└─ Output: { status, webhook_url }
```

#### 6. **services/gemini_service.py - IA Core**

```python
def query_gemini(user_query: str, context: str) -> str:
    """
    Llama Google Gemini 2.5 Flash con prompt contextualizado.
    
    Prompt structure:
    1. System: "Eres asistente académico de UTP..."
    2. Context: base_conocimiento_2026.txt
    3. User query: pregunta del usuario
    
    Returns: Respuesta formateada
    """

Funciones principales:
- query_gemini()        → Llamada a IA
- process_file()        → Procesar documentos
- get_system_prompt()   → Cargar prompt base
- format_response()     → Formatear salida
```

#### 7. **services/sheets_service.py - Data Access**

```python
# Funciones CRUD:
- get_student_schedule(student_code) → List[Schedule]
- get_student_grades(student_code) → List[Grade]
- get_teacher_info(teacher_id) → TeacherInfo
- get_course_info(course_code) → CourseInfo
- log_query(user_id, query, response, category) → None

# Fuentes de datos:
- Hoja "Horarios" → Horario de clases
- Hoja "Calificaciones" → Notas de estudiantes
- Hoja "Docentes" → Información profesores
- Hoja "Trámites" → Procedimientos administrativos
- Hoja "Logs" → Historial de consultas (analytics)
```

#### 8. **services/analytics_service.py - Métricas**

```python
def log_query(user_id, query, response, category):
    """Guarda consulta en Sheets para analytics"""

def get_metrics(days: int = 30) -> Metrics:
    """Retorna métricas de los últimos N días"""

def get_top_category() -> str:
    """Categoría más consultada"""

Métricas trackeadas:
- Queries totales (acumuladas)
- Queries por día
- Usuarios únicos
- Categorías más consultadas
- Tiempo promedio de respuesta
```

### Frontend

#### 1. **script.js - Chat Logic**

```javascript
// Funcionalidad:
- sendMessage() → POST /chat/send
- fetchHistory() → GET /chat/history
- handleLogin() → POST /auth/login
- recordAudio() → MediaRecorder + Gemini transcription
- renderMessage(role, content) → Mostrar en UI

// State management:
- sessionToken (JWT en localStorage)
- sessionId (chat session)
- chatHistory (array de mensajes)
```

#### 2. **admin.js - Dashboard Logic**

```javascript
// Funcionalidad:
- fetchAnalytics() → GET /admin/analytics
- fetchLogs() → GET /admin/logs
- fetchChartData() → GET /admin/chart-data
- renderCharts() → Chart.js
- searchLogs() → Filtro en tiempo real

// Componentes:
- KPI Cards (4 métricas principales)
- Daily Activity Chart (últimos 30 días)
- Categories Distribution (donut)
- Queries by Role (barras)
- Logs Table (búsqueda)
```

#### 3. **index.html - Chat UI**

```html
<!-- Header -->
<header>
  <h1>UTPBot - Asistente Académico</h1>
</header>

<!-- Main Chat -->
<main>
  <div id="messages-container">
    <!-- Mensajes renderizados aquí -->
  </div>
  
  <form id="chat-form">
    <input type="text" placeholder="Escribe tu pregunta...">
    <button type="submit">Enviar</button>
    <button type="button" id="mic-button">🎤 Voz</button>
  </form>
</main>

<!-- Auth Modal -->
<dialog id="auth-modal">
  <form>
    <input type="text" placeholder="Usuario">
    <input type="password" placeholder="Contraseña">
    <button type="submit">Login</button>
  </form>
</dialog>
```

#### 4. **admin.html - Dashboard UI**

```html
<!-- Sidebar Navigation -->
<aside>
  <nav>
    <a href="#dashboard">Dashboard</a>
    <a href="#logs">Logs</a>
    <a href="#telegram">Telegram Bot</a>
  </nav>
</aside>

<!-- Dashboard Section -->
<section id="dashboard">
  <!-- KPI Cards -->
  <div class="kpi-cards">
    <div class="card">Total Queries: ${totalQueries}</div>
    <div class="card">Today: ${todayQueries}</div>
    <div class="card">Active Users: ${activeUsers}</div>
    <div class="card">Top Category: ${topCategory}</div>
  </div>
  
  <!-- Charts -->
  <div class="charts">
    <canvas id="daily-activity"></canvas>
    <canvas id="categories"></canvas>
    <canvas id="by-role"></canvas>
  </div>
</section>

<!-- Logs Section -->
<section id="logs">
  <input type="text" placeholder="Buscar..." id="search">
  <table>
    <!-- Log entries -->
  </table>
</section>

<!-- Telegram Bot Section -->
<section id="telegram">
  <button id="activate-webhook">Activar Webhook</button>
</section>
```

---

## Flujos de Datos

### 1. Flujo de Login

```
Frontend → POST /auth/login (admin, password)
           ↓
Backend  → Valida credenciales
           ↓
        → Genera JWT con RS256
           ↓
        → Retorna { access_token, expires_in }
           ↓
Frontend → Guarda token en localStorage
           ↓
        → Incluye en header: Authorization: Bearer <token>
```

### 2. Flujo de Chat Completo

```
Usuario → Escribe en input + Enviar
         ↓
Frontend → POST /chat/send { message, session_id }
         ↓ + Header: Authorization: Bearer <token>
         ↓
Backend  → Middleware valida JWT
         ↓
        → Rate limiter verifica límite
         ↓
        → Route handler procesa
         ↓
        → sheets_service.get_student_context(user_id)
         ↓
        → gemini_service.query_gemini(message, context)
         ↓ (Google Gemini API - ~1-2s)
         ↓
        → analytics_service.log_query(...)
         ↓
        → Retorna { response, metadata, timestamp }
         ↓
Frontend → Renderiza respuesta en chat
         ↓
Usuario  → Ve respuesta del bot
```

### 3. Flujo de Admin Dashboard

```
Admin    → Navega a admin.html
         ↓
Frontend → POST /auth/login (si no hay token)
         ↓ + Recupera token de localStorage
         ↓
        → GET /admin/analytics
        → GET /admin/logs
        → GET /admin/chart-data
         ↓
Backend  → Valida JWT admin role
         ↓
        → Consulta analytics en Sheets
         ↓
        → Retorna datos agregados
         ↓
Frontend → Renderiza:
         ├─ KPI cards
         ├─ Chart.js graphs
         └─ Logs table con búsqueda
         ↓
Admin    → Ve métricas en tiempo real
```

### 4. Flujo de Telegram Bot

```
Usuario en Telegram → /start (comando)
                    ↓
Telegram API → POST /telegram/webhook
             ├─ event_type: message_start
             ├─ user_id: 123456
             └─ text: "/start"
                    ↓
Backend  → Route handler recibe webhook
         ↓
        → Responde 200 OK inmediato (< 5s)
         ↓
        → Background task procesa comando
         ├─ telegram_service.handle_start()
         ├─ Envía mensaje bienvenida
         └─ Guarda user en contexto
                    ↓
Telegram API → Envía respuesta al usuario
             ↓
Usuario en Telegram → Recibe: "Bienvenido a UTPBot..."
```

---

## Estructura del Repositorio

```
utpbot-academic-assistant/
│
├── 📂 backend/
│   ├── main.py                    # Entry point FastAPI
│   ├── requirements.txt            # Dependencies
│   │
│   ├── 📂 routes/
│   │   ├── auth.py               # POST /auth/*
│   │   ├── chat.py               # POST /chat/*
│   │   ├── admin.py              # GET /admin/*
│   │   ├── docente.py            # Gestión docentes
│   │   ├── telegram.py           # POST /telegram/*
│   │   └── transcribe.py         # POST /transcribe
│   │
│   ├── 📂 services/
│   │   ├── gemini_service.py     # IA Core (Google Gemini)
│   │   ├── sheets_service.py     # Data access (Google Sheets)
│   │   ├── telegram_service.py   # Bot logic (Telegram)
│   │   ├── analytics_service.py  # Metrics (Query logging)
│   │   └── calendar_service.py   # Scheduling (Google Calendar)
│   │
│   ├── 📂 models/
│   │   └── schemas.py            # Pydantic models (DTOs)
│   │
│   ├── 📂 utils/
│   │   └── (helper functions)
│   │
│   └── .env.example              # Template de variables
│
├── 📂 frontend/
│   ├── index.html                # Chat interface
│   ├── admin.html                # Admin dashboard
│   ├── script.js                 # Chat logic
│   ├── admin.js                  # Dashboard logic
│   ├── style.css                 # Chat styles
│   ├── admin.css                 # Dashboard styles
│   └── UTP.png                   # Logo
│
├── 📂 docs/
│   ├── ARQUITECTURA.md           # (Este archivo)
│   ├── STACK.md                  # Stack detallado
│   ├── DEPLOYMENT.md             # Guía despliegue
│   └── PRODUCCION.md             # Checklist producción
│
├── 📄 README.md                  # Documentación principal
├── 📄 Procfile                   # PaaS config
├── 📄 railway.toml               # Railway config
├── 📄 vercel.json                # Vercel config
│
└── 📚 Knowledge Base
    ├── UTP_Base_Conocimiento_2026.pdf
    └── utp_info.txt
```

---

## Consideraciones de Diseño

### 1. **Separación de Responsabilidades**
- Routes: Solo manejo HTTP
- Services: Lógica de negocio
- Models: Validación de datos
- Utils: Funciones reutilizables

### 2. **Async/Await**
- FastAPI es async nativo
- Todos los I/O es asincrónico
- Mejor performance bajo carga

### 3. **Seguridad en Capas**
```
Capa 1: CORS Middleware    → Orígenes permitidos
Capa 2: Auth Middleware    → JWT válido
Capa 3: Rate Limiter       → Límites por IP
Capa 4: Route Logic        → Validación negocio
Capa 5: Database Access    → Checks finales
```

### 4. **Error Handling**
```python
try:
    # Lógica principal
except ValidationError:
    # 422 Unprocessable Entity
except AuthError:
    # 401 Unauthorized
except RateLimitError:
    # 429 Too Many Requests
except Exception:
    # 500 Internal Server Error
```

---

## Performance Targets

| Métrica | Target | Descripción |
|---------|--------|------------|
| Response Time | < 2s | Incluido Gemini API |
| Chat Latency | < 3s | User percibido |
| Admin Load | < 1s | Dashboard initial load |
| Availability | > 99.5% | Monthly uptime |
| Error Rate | < 0.5% | 5xx errors |
| Concurrent Users | 500+ | Peak load |

---

## Próximas Evoluciones

1. **Database Migration** → PostgreSQL para analytics
2. **WebSocket Chat** → Real-time (sin polling)
3. **Redis Cache** → Session caching
4. **Search** → Full-text search en logs
5. **Mobile App** → React Native
6. **AI Tuning** → Fine-tuning Gemini para UTP

---

**Documento de Referencia - UTPBot v2.0.0**  
*Última actualización: Agosto 2026*
