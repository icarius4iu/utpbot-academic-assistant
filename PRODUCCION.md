# ✅ Checklist de Producción - UTPBot v2.0.0

**Este documento es tu guía final antes de llevar UTPBot a producción.**

---

## 📋 Tabla de Contenidos
1. [Pre-Deploy Checklist](#pre-deploy-checklist)
2. [Configuración de Seguridad](#configuración-de-seguridad)
3. [Testing Checklist](#testing-checklist)
4. [Performance & Monitoring](#performance--monitoring)
5. [Go-Live Runbook](#go-live-runbook)
6. [Post-Launch Monitoring](#post-launch-monitoring)

---

## Pre-Deploy Checklist

### ⚙️ Configuración Backend

- [ ] **JWT Secret Key**
  - Generado con `python -c "import secrets; print(secrets.token_hex(32))"`
  - Mínimo 64 caracteres hex
  - Almacenado en Railway variables
  - NO compartido en repos

- [ ] **Admin Credentials**
  - Username único (no "admin")
  - Password mínimo 25 caracteres
  - Incluye: mayúsculas, minúsculas, números, símbolos
  - Cambiar a credenciales reales para producción

- [ ] **Debug Mode Desactivado**
  - `DEBUG=false` en Railway
  - `/docs` endpoint oculto
  - `/redoc` endpoint oculto
  - Logging reducido (INFO level)

- [ ] **CORS Correctamente Configurado**
  - Solo dominios permitidos
  - Sin wildcards (*)
  - Incluir: https://app.vercel.app + dominio personalizado
  - Ejemplo: `CORS_ORIGINS=https://app.utp.edu.pe,https://app.vercel.app`

- [ ] **Google Services**
  - [ ] Google Cloud Project creado
  - [ ] Gemini API habilitada
  - [ ] Google Sheets API habilitada
  - [ ] Google Calendar API habilitada (si se usa)
  - [ ] Service Account creado
  - [ ] JSON credentials descargado y segurizados
  - [ ] Spreadsheet compartido con service account
  - [ ] SPREADSHEET_ID verificado
  - [ ] GEMINI_API_KEY verificado y funciona

- [ ] **Telegram Bot (si se usa)**
  - [ ] Bot creado via @BotFather
  - [ ] Token almacenado en Railway
  - [ ] Webhook URL actualizada: `https://tu-backend/telegram/webhook`
  - [ ] Comandos registrados en BotFather

- [ ] **Database & Storage**
  - [ ] Google Sheets conexión verificada
  - [ ] Datos académicos importados correctamente
  - [ ] Backup de datos realizados
  - [ ] Permisos correctos configurados

### 🎨 Configuración Frontend

- [ ] **API Base URL**
  - Actualizada a URL de Railway en producción
  - Ejemplo: `const API_BASE = 'https://utpbot-api-prod.railway.app'`
  - NO usar localhost
  - HTTPS obligatorio

- [ ] **Admin HTML Path**
  - `/admin` o `/admin.html` accesible
  - Requiere login para acceder
  - Verificar seguridad de rutas

- [ ] **Assets Optimizados**
  - [ ] Images comprimidas
  - [ ] CSS minificado (opcional)
  - [ ] JavaScript minificado (opcional)
  - [ ] No console.logs debug visible

- [ ] **Responsividad Verificada**
  - [ ] Desktop (1920x1080)
  - [ ] Tablet (768x1024)
  - [ ] Mobile (375x667)
  - [ ] Landscape orientations

### 📦 Infrastructure & Deployment

- [ ] **Railway**
  - [ ] Proyecto creado
  - [ ] Git conectado
  - [ ] Todas las variables de entorno agregadas
  - [ ] Python 3.11 especificado
  - [ ] Health check configurado: `/health`
  - [ ] Memory limits razonables
  - [ ] Restart policy: ON_FAILURE

- [ ] **Vercel**
  - [ ] Proyecto creado
  - [ ] GitHub conectado
  - [ ] Output directory: `frontend/`
  - [ ] No build command
  - [ ] Preview deployments habilitados
  - [ ] Production environment configured

- [ ] **Dominios**
  - [ ] Dominio backend (api.utp.edu.pe) o usar Railway URL
  - [ ] Dominio frontend (app.utp.edu.pe) o usar Vercel URL
  - [ ] DNS records configurados (CNAME)
  - [ ] SSL certificates válidos (auto-renovable)

- [ ] **CI/CD**
  - [ ] GitHub Actions verificado (si aplica)
  - [ ] Tests pasan en main branch
  - [ ] Linting setup (optional)
  - [ ] Pre-commit hooks (optional)

---

## Configuración de Seguridad

### 🔐 Autenticación & Autorización

- [ ] **JWT Implementation**
  ```python
  # Verificar en routes/auth.py:
  - Algoritmo: HS256 ✅
  - Secret key: mínimo 64 caracteres ✅
  - Expiration: 8 horas ✅
  - Bearer token en Header ✅
  - Validación en todos los endpoints ✅
  ```

- [ ] **Admin Role Protection**
  - [ ] `/admin/*` requiere admin role
  - [ ] `/admin/analytics` protegido
  - [ ] `/admin/logs` protegido
  - [ ] Telegram webhook activation protegido

- [ ] **Password Hashing (Opcional)**
  - Si se implementa auth más compleja:
  - Usar bcrypt o argon2
  - NO almacenar passwords en texto plano

### 🚫 Rate Limiting

- [ ] **slowapi Configurado**
  - [ ] Global: 200 requests/hora
  - [ ] Per-IP: 30 requests/minuto
  - [ ] Protege: /chat/send, /auth/login, /telegram/webhook
  - [ ] Retorna: 429 Too Many Requests

- [ ] **Testing**
  ```bash
  # Verificar rate limiting funciona:
  for i in {1..35}; do
    curl -X POST https://backend/chat/send \
      -H "Authorization: Bearer $TOKEN"
  done
  # Después de 30, debe recibir 429
  ```

### 🛡️ Security Headers

- [ ] **OWASP Headers Presentes**
  ```
  X-Content-Type-Options: nosniff ✅
  X-Frame-Options: DENY ✅
  X-XSS-Protection: 1; mode=block ✅
  Strict-Transport-Security: max-age=31536000 (PROD) ✅
  Referrer-Policy: strict-origin-when-cross-origin ✅
  Permissions-Policy: camera=(), geolocation=() ✅
  Cache-Control: no-store, no-cache, must-revalidate ✅
  ```

- [ ] **Verificar Headers**
  ```bash
  curl -I https://backend.railway.app/health
  # Debe mostrar todos los headers arriba
  ```

### 🔒 Secrets Management

- [ ] **Variables de Entorno**
  - [ ] JWT_SECRET_KEY seguro
  - [ ] ADMIN_PASSWORD seguro
  - [ ] GEMINI_API_KEY almacenado
  - [ ] GOOGLE_CREDENTIALS_FILE seguro
  - [ ] TELEGRAM_BOT_TOKEN almacenado
  - [ ] Nada en .env commiteado a Git
  - [ ] .env en .gitignore

- [ ] **Acceso Restringido**
  - [ ] Solo admins conocen credentials
  - [ ] Nada en logs/console
  - [ ] Railway vault encriptado
  - [ ] Rotación de secrets cada 6 meses

### 🔍 Vulnerabilities Scan

- [ ] **Python Dependencies**
  ```bash
  # Ejecutar antes de deploy:
  pip audit
  # Debe mostrar 0 vulnerabilities críticas
  ```

- [ ] **Static Analysis**
  ```bash
  # Opcional pero recomendado:
  pip install bandit
  bandit -r backend/
  ```

- [ ] **Dependency Updates**
  - [ ] pip-audit ejecutado
  - [ ] Dependabot habilitado en GitHub
  - [ ] No usar versiones muy antiguas

---

## Testing Checklist

### ✅ Unit Testing (Backend)

- [ ] **Routes Testing**
  ```python
  # Test authentication:
  - [ ] POST /auth/login con credenciales válidas → 200
  - [ ] POST /auth/login con credenciales inválidas → 401
  - [ ] POST /auth/verify con token expirado → 401
  ```

- [ ] **Chat Testing**
  ```python
  # Test chat endpoints:
  - [ ] POST /chat/send sin auth → 401
  - [ ] POST /chat/send con mensaje válido → 200
  - [ ] POST /chat/send con mensaje malformado → 422
  ```

- [ ] **Rate Limiting Testing**
  ```python
  # Test rate limits:
  - [ ] 30 requests en 60 segundos → OK (últimos OK)
  - [ ] 31+ requests en 60 segundos → 429
  ```

### 🔗 Integration Testing

- [ ] **Chat Flow E2E**
  1. Login (frontend)
  2. Send message
  3. Receive response
  4. Logout
  5. Token no longer valid

- [ ] **Admin Dashboard**
  1. Login
  2. Load analytics
  3. Fetch logs
  4. Search logs
  5. See charts render

- [ ] **Telegram Bot**
  1. Send /start command
  2. Receive welcome message
  3. Send /ayuda command
  4. See command list
  5. Send /salir to logout

### 🧪 Frontend Testing (Manual)

- [ ] **Chat Interface**
  - [ ] Login works
  - [ ] Can send text messages
  - [ ] Receive responses
  - [ ] Messages display correctly
  - [ ] Typing indicator works
  - [ ] Scroll to latest message
  - [ ] Mobile responsive

- [ ] **Admin Dashboard**
  - [ ] Load without errors
  - [ ] KPI cards display
  - [ ] Charts render (Chart.js)
  - [ ] Logs table has data
  - [ ] Search works in logs
  - [ ] Mobile responsive

- [ ] **Authentication**
  - [ ] Login with correct credentials
  - [ ] Reject wrong credentials
  - [ ] Session persists on refresh
  - [ ] Logout clears token
  - [ ] Token expiration redirects to login

- [ ] **Browser Compatibility**
  - [ ] Chrome (latest)
  - [ ] Firefox (latest)
  - [ ] Safari (latest)
  - [ ] Edge (latest)

- [ ] **Mobile Devices**
  - [ ] iPhone SE
  - [ ] iPhone 13 Pro
  - [ ] Samsung Galaxy
  - [ ] Tablet (iPad)

### 🔐 Security Testing

- [ ] **XSS Prevention**
  ```javascript
  // Test: Enviar <script> en mensaje
  // Input: <img src=x onerror="alert('XSS')">
  // Expected: Message displayed safely, no alert
  ```

- [ ] **CSRF Protection**
  ```javascript
  // Test: Cambiar origen en headers
  // Expected: CORS error, request rechazado
  ```

- [ ] **Authentication Bypass**
  ```bash
  # Test: Modificar JWT token
  # Expected: 401 Unauthorized
  
  # Test: Enviar request sin Authorization header
  # Expected: 401 Unauthorized
  ```

- [ ] **SQL Injection (N/A)**
  - No usar SQL directo (usando Sheets API)
  - Pero verificar no hay inyección en Sheets queries

---

## Performance & Monitoring

### ⚡ Performance Targets

| Métrica | Target | Cómo Medir |
|---------|--------|----------|
| API Response | < 100ms | DevTools Network |
| Chat Latency | < 3s | Incluye Gemini |
| Admin Load | < 1s | Vercel Analytics |
| Page Load | < 2s | Lighthouse |
| FCP | < 1.5s | Web Vitals |
| LCP | < 2.5s | Web Vitals |
| CLS | < 0.1 | Web Vitals |

### 📊 Monitoring Setup

- [ ] **Railway Monitoring**
  - [ ] Logs enabled
  - [ ] Metrics dashboard setup
  - [ ] Alerts configured (optional)
  - [ ] CPU/Memory monitored

- [ ] **Vercel Analytics**
  - [ ] Analytics habilitado
  - [ ] Web Vitals tracked
  - [ ] Visitor metrics visible

- [ ] **Error Tracking (Opcional)**
  - [ ] Sentry setup (recommended)
  - [ ] Error notifications
  - [ ] Stack traces captured

- [ ] **Uptime Monitoring**
  - [ ] Healthcheck endpoint
  - [ ] Uptime monitoring service (UptimeRobot, etc)
  - [ ] Alerts si cae backend

### 📈 Initial Metrics Baseline

```
Después de deploy, registrar:
- Request latency: avg/p95/p99
- Error rate: % de 5xx
- Available users: concurrentes
- Storage usage: Sheets usage
- API quota usage: Gemini tokens/día
```

---

## Go-Live Runbook

### 📅 Pre-Go-Live (24 horas antes)

```
[ ] Backup de todos los datos
[ ] Todos los tests pasan
[ ] Security scan completado
[ ] Documentación actualizada
[ ] Team briefing completado
[ ] Rollback plan confirmado
[ ] Monitoring setup verificado
```

### 🚀 Go-Live Day

**08:00 - Start**
- [ ] Verificar todos los systems están UP
- [ ] Backup final de datos
- [ ] Team en standby

**08:30 - Cutover**
- [ ] Actualizar DNS (si aplica)
- [ ] Cambiar CORS_ORIGINS a dominios reales
- [ ] Activar Telegram webhook
- [ ] Notificar a usuarios

**09:00 - Smoke Testing**
- [ ] Login works
- [ ] Chat sends message
- [ ] Admin dashboard loads
- [ ] Telegram bot responds
- [ ] Analytics being logged

**09:30 - Monitor**
- [ ] Monitorear logs cada 5 min
- [ ] CPU/Memory usage normal
- [ ] Error rate < 0.5%
- [ ] Response times < 3s

**10:00 - Full Testing**
- [ ] User acceptance testing
- [ ] Edge cases testing
- [ ] Load testing básico
- [ ] Cross-browser testing

**11:00 - Stabilization**
- [ ] Si todo bien → Go-Live oficial
- [ ] Si hay issues → Rollback plan

### 🔄 Rollback Procedure

**Si algo falla:**

1. **Immediate (< 5 min)**
   ```bash
   # Railway: Redeploy previous version
   Railway Dashboard → Deployments → Previous → Redeploy
   
   # Vercel: Promote previous production
   Vercel Dashboard → Deployments → Previous → Promote
   ```

2. **Communication**
   - Notificar a team
   - Notificar a usuarios si es necesario
   - Post-mortem después

3. **Investigation**
   - Revisar logs completos
   - Identificar root cause
   - Fix en local environment
   - Test thoroughly antes de re-deploy

---

## Post-Launch Monitoring

### 🔍 Day 1 Monitoring

- [ ] **Every 30 minutes**
  - [ ] Backend health check
  - [ ] Frontend load time
  - [ ] Error rate
  - [ ] User activity (if applicable)

- [ ] **Automated Alerts Setup**
  ```
  Alert if:
  - Backend response > 3s (average)
  - Error rate > 1%
  - API downtime
  - Disk space > 80%
  - Memory usage > 80%
  ```

### 📅 Week 1 Monitoring

- [ ] Daily metrics review
- [ ] Performance tracking
- [ ] Bug reports consolidation
- [ ] User feedback collection
- [ ] Database growth tracking

### 📊 Ongoing Monitoring

**Weekly:**
- [ ] Performance metrics (avg latency, errors)
- [ ] User metrics (active users, sessions)
- [ ] Storage metrics (Sheets usage, logs growth)
- [ ] API quota usage (Gemini tokens)

**Monthly:**
- [ ] Performance trend analysis
- [ ] Cost analysis (Railway + Vercel)
- [ ] Security audit (if any issues)
- [ ] Capacity planning (do we need scaling?)

**Quarterly:**
- [ ] Full system health review
- [ ] Dependency updates
- [ ] Architecture review
- [ ] User feedback analysis
- [ ] Roadmap update

---

## Knowledge Transfer

- [ ] **Team Training**
  - [ ] Developers know: ARQUITECTURA.md, STACK.md
  - [ ] DevOps knows: DEPLOYMENT.md, monitoring setup
  - [ ] Admins know: how to reset passwords, manage bot

- [ ] **Documentation**
  - [ ] README.md actualizado
  - [ ] API docs generado (/docs)
  - [ ] Runbooks para incidents
  - [ ] Contact list para emergencias

- [ ] **Access Management**
  - [ ] Railway access shared
  - [ ] Vercel access shared
  - [ ] Google Cloud access shared
  - [ ] GitHub access shared
  - [ ] Telegram Bot admin panel access

---

## Post-Launch Checklist (After 1 Week)

- [ ] **Performance**
  - [ ] Average latency stable
  - [ ] No memory leaks (check trends)
  - [ ] Error rate < 0.5%

- [ ] **User Feedback**
  - [ ] No critical issues reported
  - [ ] UX is intuitive
  - [ ] Mobile experience good

- [ ] **Operations**
  - [ ] Logs are clear
  - [ ] Alerts working correctly
  - [ ] Database queries optimized

- [ ] **Security**
  - [ ] No security incidents
  - [ ] All secrets secure
  - [ ] Firewall rules correct

- [ ] **Documentation**
  - [ ] All docs up to date
  - [ ] Procedures documented
  - [ ] Contact info correct

---

## Incident Response Plan

### If Backend Goes Down

```
1. Immediate (< 5 min):
   - Verify with: curl https://backend/health
   - Check Railway logs for error
   - Check if service restarting

2. Response (5-15 min):
   - If restarting: wait for recovery
   - If error: check environment variables
   - Check database connectivity
   - Check API quota (Gemini, Sheets)

3. Communication:
   - Notify team
   - Notify stakeholders if > 5 min
   - Prepare status update

4. Resolution:
   - Fix issue in code/config
   - Test in staging
   - Redeploy to production
   - Verify with smoke tests
   - Post-mortem if > 30 min downtime
```

### If Frontend Has Issues

```
1. Immediate:
   - Check Vercel deployment status
   - Clear browser cache
   - Test in incognito mode

2. Investigation:
   - Check DevTools console for JS errors
   - Verify API connectivity
   - Test on multiple browsers

3. Fix:
   - Fix in local environment
   - Commit to main
   - Vercel auto-redeploys
   - Verify with smoke tests
```

### If Telegram Bot Not Responding

```
1. Check:
   - getWebhookInfo status
   - Backend logs for webhook errors
   - Token validity

2. Fix:
   - Reactivate webhook via admin panel
   - Or: setWebhook via Telegram API manually
   - Verify with test message

3. Verify:
   - Send /start command
   - Check response received
   - Check logs for processing
```

---

## Success Criteria

### Launch Success ✅

- [ ] 0 critical issues in first 24 hours
- [ ] Average response time < 2 seconds
- [ ] Uptime > 99%
- [ ] No security incidents
- [ ] All features working as expected
- [ ] User feedback positive

### Ongoing Success 📈

- [ ] Consistent performance metrics
- [ ] < 0.5% error rate
- [ ] Growing user base
- [ ] Positive feedback
- [ ] Regular updates and improvements
- [ ] Team satisfaction

---

## Useful Links & Resources

- **Railway Dashboard**: https://railway.app
- **Vercel Dashboard**: https://vercel.com
- **Google Cloud Console**: https://console.cloud.google.com
- **Telegram Bot API**: https://core.telegram.org/bots/api
- **Google Gemini Docs**: https://ai.google.dev/
- **FastAPI Docs**: https://fastapi.tiangolo.com

---

## Support & Escalation

### Contacts

- **Tech Lead**: [Name] ([email])
- **DevOps**: [Name] ([email])
- **Security**: [Name] ([email])
- **Product**: [Name] ([email])

### Escalation Path

1. **P4 (Low)** → Team lead within 24h
2. **P3 (Medium)** → Tech lead within 4h
3. **P2 (High)** → Full team + stakeholders within 1h
4. **P1 (Critical)** → All hands on deck, immediately

---

**Checklist Completo de Producción - UTPBot v2.0.0**  
*Última actualización: Agosto 2026*  
*Versión: 1.0*

**Estado Actual:** ⏳ Pendiente de Producción  
**Última Revisión:** [Fecha]  
**Completitud:** __% (llenar cuando vayas completando items)
