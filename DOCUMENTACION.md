# 📖 Índice Completo de Documentación - UTPBot v2.0.0

## 🎯 ¿Por dónde empiezo?

**Si vas a llevar UTPBot a producción, sigue este orden:**

```
START
  ↓
1. STACK.md (entiende las tecnologías)
  ↓
2. ARQUITECTURA.md (entiende el diseño)
  ↓
3. DEPLOYMENT.md (sigue los pasos)
  ↓
4. PRODUCCION.md (completa el checklist)
  ↓
END ✅ LISTO PARA PRODUCCIÓN
```

---

## 📄 Documentos Disponibles

### 1. **STACK.md** - Stack Tecnológico
**Para:** Developers, Architects  
**Tiempo:** 15-20 min  
**Contiene:**
- Backend: FastAPI, Python, dependencias
- Frontend: HTML5, CSS3, JavaScript vanilla
- Infrastructure: Railway, Vercel, Docker
- Comparativas de tecnologías
- Performance benchmarks
- Escalabilidad y mejoras futuras

**Debe saber:** Qué herramientas usamos y por qué

---

### 2. **ARQUITECTURA.md** - Diseño del Sistema
**Para:** Developers, Architects, Tech Leads  
**Tiempo:** 20-30 min  
**Contiene:**
- Diagrama de componentes
- Flujos de datos (chat, auth, admin)
- Estructura del repositorio
- Componentes backend (routes, services)
- Componentes frontend (HTML, JS)
- Consideraciones de diseño
- Performance targets

**Debe saber:** Cómo funciona el sistema internamente

---

### 3. **DEPLOYMENT.md** - Guía de Despliegue
**Para:** DevOps, Developers  
**Tiempo:** 45-60 min (incluye pasos de ejecución)  
**Contiene:**
- Pre-requisitos (cuentas, secretos)
- Configuración local
- Despliegue backend en Railway
- Despliegue frontend en Vercel
- Configuración Telegram webhook
- Verificación post-deploy
- Troubleshooting completo

**Debe saber:** Cómo poner el sistema en producción

---

### 4. **PRODUCCION.md** - Checklist de Producción
**Para:** Todos (especialmente responsable de producción)  
**Tiempo:** 30-40 min (review del checklist)  
**Contiene:**
- Pre-deploy checklist (400+ items)
- Configuración de seguridad
- Testing checklist
- Performance & monitoring
- Go-live runbook
- Rollback procedures
- Post-launch monitoring
- Incident response plan

**Debe saber:** Qué verificar antes de ir live

---

## 🗂️ Estructura de Documentación

```
utpbot-academic-assistant/
│
├── README.md                 # Landing del proyecto
├── DOCUMENTACION.md          # (Este archivo) - Índice
├── STACK.md                  # Stack tecnológico ⭐
├── ARQUITECTURA.md           # Diseño del sistema ⭐
├── DEPLOYMENT.md             # Guía de despliegue ⭐
├── PRODUCCION.md             # Checklist producción ⭐
│
├── backend/                  # Código FastAPI
│   ├── main.py
│   ├── requirements.txt
│   ├── routes/
│   ├── services/
│   └── models/
│
├── frontend/                 # Código HTML/CSS/JS
│   ├── index.html
│   ├── admin.html
│   ├── script.js
│   └── style.css
│
└── docs/
    └── (espacio para más docs si es necesario)
```

---

## 🎓 Guías por Rol

### 👨‍💻 Desarrollador Backend

**Lee primero:**
1. STACK.md - Backend section
2. ARQUITECTURA.md - Componentes backend
3. DEPLOYMENT.md - Local setup

**Tareas:**
- [ ] Entender FastAPI architecture
- [ ] Conocer rutas y servicios
- [ ] Saber cómo ejecutar localmente
- [ ] Entender JWT y rate limiting

---

### 👨‍💻 Desarrollador Frontend

**Lee primero:**
1. STACK.md - Frontend section
2. ARQUITECTURA.md - Componentes frontend
3. DEPLOYMENT.md - Frontend setup

**Tareas:**
- [ ] Entender interfaz de chat
- [ ] Conocer admin dashboard
- [ ] Saber cómo conectar a API
- [ ] Entender autenticación JWT

---

### 🛠️ DevOps / Infra

**Lee primero:**
1. STACK.md - Infrastructure section
2. DEPLOYMENT.md - Completo
3. PRODUCCION.md - Monitoring section

**Tareas:**
- [ ] Configurar Railway
- [ ] Configurar Vercel
- [ ] Setup monitoreo
- [ ] Crear runbooks

---

### 👔 Tech Lead / Architect

**Lee primero:**
1. STACK.md - Completo
2. ARQUITECTURA.md - Completo
3. PRODUCCION.md - Completo

**Tareas:**
- [ ] Revisar decisiones técnicas
- [ ] Validar performance targets
- [ ] Aprobar go-live
- [ ] Planificar mejoras

---

### 🚀 Release Manager / Product

**Lee primero:**
1. DEPLOYMENT.md - Resumen
2. PRODUCCION.md - Go-live runbook

**Tareas:**
- [ ] Coordinar despliegue
- [ ] Comunicar a usuarios
- [ ] Monitorear métricas
- [ ] Coordinar rollback si es necesario

---

## ⚡ Quick Reference

### Tecnologías Principales

```
Backend:  FastAPI 0.115.0 + Uvicorn 0.30.6
Frontend: HTML5 + CSS3 + JavaScript vanilla
IA:       Google Gemini 2.5 Flash
Data:     Google Sheets API
Bot:      Telegram Bot API
Deploy:   Railway + Vercel
```

### URLs Importantes (Producción)

```
Frontend:  https://app.utp.edu.pe (o https://utpbot.vercel.app)
Backend:   https://api.utp.edu.pe (o https://utpbot-api.railway.app)
Swagger:   https://api.utp.edu.pe/docs (solo desarrollo)
Health:    https://api.utp.edu.pe/health
```

### Comandos Útiles

```bash
# Backend
python -m venv venv
source venv/bin/activate
pip install -r backend/requirements.txt
uvicorn main:app --reload --port 8000

# Frontend
python -m http.server 5500

# Testing
curl https://api.utp.edu.pe/health
curl -X POST https://api.utp.edu.pe/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"xxx"}'

# Git
git push origin main  # Auto-deploy en Railway + Vercel
```

### Variables de Entorno Críticas

```env
# SEGURIDAD - GENERAR NUEVAS PARA PRODUCCIÓN
JWT_SECRET_KEY=<64-char-hex>
ADMIN_PASSWORD=<25-chars-min>

# Google
GEMINI_API_KEY=xxx
SPREADSHEET_ID=xxx
GOOGLE_CREDENTIALS_FILE=credentials.json

# Telegram
TELEGRAM_BOT_TOKEN=xxx
TELEGRAM_WEBHOOK_URL=https://api.utp.edu.pe/telegram/webhook

# CORS
CORS_ORIGINS=https://app.utp.edu.pe,https://app.vercel.app

# Debug
DEBUG=false (en producción)
```

---

## 📊 Decisiones de Diseño Clave

### Por qué FastAPI?
✅ Auto docs (Swagger)  
✅ Async nativo  
✅ Tipado con Pydantic  
✅ Performance excelente

### Por qué Railway + Vercel?
✅ Fácil de usar  
✅ Económico ($5-15/mes)  
✅ Auto-scaling  
✅ Zero-downtime deploys

### Por qué Google Gemini?
✅ Muy rápido (2.5 Flash)  
✅ Buena calidad  
✅ Pricing razonable  
✅ Token limit generoso (1M)

### Por qué JavaScript vanilla (no React/Vue)?
✅ Menor footprint  
✅ Sin build step  
✅ Despliegue inmediato  
✅ Perfectamente adecuado para SPA simple

---

## 🎯 Milestones

```
✅ MVP (v1.0)           - Chat básico + Sheets
✅ Admin Dashboard      - Métricas y logs
✅ Security Hardening   - JWT + Rate limiting + Headers
✅ Telegram Bot         - Comandos y webhooks
✅ Production Ready     - Checklists y monitoring
  
🔮 PostgreSQL Migration - Analytics en DB (futuro)
🔮 WebSocket Chat       - Real-time (futuro)
🔮 Mobile App           - React Native (futuro)
```

---

## ✅ Checklists por Etapa

### Pre-Development
- [ ] Team setup
- [ ] Cuentas creadas (GCP, Railway, Vercel, Telegram)
- [ ] Repositorio clonado
- [ ] Local environment running

### Development
- [ ] Features implementadas
- [ ] Tests pasando
- [ ] Code review completado
- [ ] Merged a main

### Staging
- [ ] Deployed a staging environment
- [ ] QA testing completado
- [ ] Performance benchmarks OK
- [ ] Security audit OK

### Production
- [ ] Pre-deploy checklist 100%
- [ ] Go-live runbook ejecutado
- [ ] Post-launch monitoring 24/7
- [ ] Team on-call por 48h

---

## 📞 Soporte & Escalación

### Problemas Comunes

**"API retorna 401 Unauthorized"**
→ Ver DEPLOYMENT.md - Troubleshooting - Auth errors

**"Rate limiting bloqueando usuarios"**
→ Ver ARQUITECTURA.md - Rate Limiting

**"Telegram bot no responde"**
→ Ver DEPLOYMENT.md - Configuración Telegram Webhook

**"Performance lento"**
→ Ver STACK.md - Performance Benchmarks

### Contactos

```
Tech Lead:  [nombre] - [email]
DevOps:     [nombre] - [email]
Frontend:   [nombre] - [email]
Backend:    [nombre] - [email]
```

---

## 📚 Referencias Externas

- **FastAPI Docs**: https://fastapi.tiangolo.com
- **Railway Docs**: https://docs.railway.app
- **Vercel Docs**: https://vercel.com/docs
- **Google Gemini API**: https://ai.google.dev
- **Telegram Bot API**: https://core.telegram.org/bots/api
- **OWASP Security Headers**: https://owasp.org/www-project-secure-headers

---

## 📝 Versionamiento

| Versión | Fecha | Cambios | Estado |
|---------|-------|---------|--------|
| 2.0.0 | Ago 2026 | Admin Dashboard, Security Hardening, Telegram Bot | ✅ Production Ready |
| 1.0.0 | Jul 2026 | MVP Chat + Sheets | ✅ Archived |

---

## 🎓 Learning Path

```
Día 1: Lee STACK.md + ARQUITECTURA.md
       (2-3 horas)

Día 2: Setup local (DEPLOYMENT.md)
       (1-2 horas)

Día 3: Explora código
       (3-4 horas)

Día 4: Review PRODUCCION.md
       (1-2 horas)

Día 5: Go-live
       (2-3 horas)
```

---

**Índice Completo de Documentación - UTPBot v2.0.0**  
*Última actualización: Agosto 2026*  
*Mantenido por: Tech Team*
