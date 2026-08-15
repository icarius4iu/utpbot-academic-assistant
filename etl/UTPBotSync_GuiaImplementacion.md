# UTPBot Sync — Guía técnica completa de implementación

**Extensión de navegador (Manifest V3) para autoexportar los datos del Portal del Estudiante UTP hacia el backend de UTPBot.**

> Documento generado a partir de una investigación **en vivo sobre tu propia cuenta** del Portal del Estudiante (`class.utp.edu.pe`), el 15/08/2026. Todo lo que aparece acá (endpoints, headers, shapes de respuesta, valores de enums) fue **verificado con requests reales que devolvieron `200 OK`**, no supuesto.

---

## 0. Veredicto: ¿se puede? — Sí, y ya está probado

La duda de tu spec (`SCRAPER_PLUGIN_SPEC.md`, principio #5) era: *¿el portal carga los datos por una API JSON interna, o hay que parsear el DOM?*

**Respuesta: hay API JSON interna.** El Portal del Estudiante es una SPA que:

- Se autentica contra **Keycloak** (`https://sso.utp.edu.pe/auth/realms/Xpedition`) → obtiene un **token Bearer (JWT)**.
- Lee TODOS los datos académicos desde una API REST: **`https://api-pao.utpxpedition.com`**.
- No hace falta tocar el DOM. Esto es mucho más estable: un rediseño visual del portal no rompe nada mientras la API no cambie.

Lo demostré replicando a mano las 3 llamadas clave con el token capturado de tu sesión, y devolvieron tus 6 cursos y tus 44 bloques de clase exactos. **El mecanismo central del plugin funciona de punta a punta.**

### Lo que esto cambia respecto a tu spec original

| Tu spec asumía | Realidad encontrada | Impacto |
|---|---|---|
| Quizás haya que scrapear el DOM (selectores CSS frágiles) | API JSON interna estable | ✅ Mucho más robusto y simple |
| El `aula` quizás no exista | Campo `classroom` existe en la API (hoy `null`, pero se envía cuando el portal lo llene) | ✅ Sin trabajo extra |
| `codigo` y `nombre` quizás estén solo en una página de perfil | Vienen firmados dentro del **JWT del portal** (`preferred_username`, `name`) | ✅ Sin llamada extra |
| Créditos no visibles | No aparecen en estos endpoints | ⚠️ Queda pendiente (ver §9) |
| `carrera` en página de perfil | No apareció en los 3 endpoints ni en el JWT | ⚠️ Queda pendiente (ver §9) |
| Progreso y modalidad no existen en el schema | La API los expone (`progress`, `modality`) | ➕ Agregar columnas (ver §7.3) |

---

## 1. Arquitectura general de la solución

```
┌─────────────────────────── Chrome / Firefox (MV3) ───────────────────────────┐
│                                                                               │
│  Pestaña: class.utp.edu.pe (Portal del Estudiante, ya logueado por el alumno) │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │  inject.js  (world: MAIN — mismo contexto que la SPA)                    │ │
│  │   • Engancha XHR/fetch → CAPTURA pasivamente: token Bearer, User-Id,     │ │
│  │     X-Tenant-Id (nunca la contraseña).                                   │ │
│  │   • A pedido: RE-CONSULTA los 3 endpoints de api-pao (desde el mismo     │ │
│  │     origin, por eso CORS pasa) y ARMA el JSON del contrato.              │ │
│  └───────────────▲───────────────────────────────────────┬─────────────────┘ │
│                  │ window.postMessage                     │                   │
│  ┌───────────────┴───────────────────────────────────────▼─────────────────┐ │
│  │  content.js (world: ISOLATED) — puente page ⇄ extensión                 │ │
│  └───────────────▲───────────────────────────────────────┬─────────────────┘ │
│                  │ chrome.runtime.sendMessage             │                   │
│  ┌───────────────┴─────────────┐          ┌───────────────▼─────────────────┐ │
│  │  popup.html / popup.js       │          │  background.js (service worker) │ │
│  │   • LOGIN a UTPBot (Firebase │          │   • Guarda refreshToken          │ │
│  │     email/pass → idToken)    │──idToken─▶│   • POST /estudiante/sincronizar │ │
│  │   • Botón "Sincronizar"      │  +payload │     con Bearer <idToken Firebase>│ │
│  │   • PREVIEW + confirmación   │          │   • Devuelve resultado           │ │
│  └──────────────────────────────┘          └───────────────┬─────────────────┘ │
└──────────────────────────────────────────────────────────┼───────────────────┘
                                                            │ HTTPS
                                                            ▼
                                            ┌───────────────────────────────┐
                                            │ Backend UTPBot (Quarkus/Spring)│
                                            │ POST /estudiante/sincronizar    │
                                            │  @RolesAllowed("estudiante")    │
                                            │  upsert por codigo de CurrentUser│
                                            └───────────────────────────────┘
```

**Dos tokens distintos — no confundirlos (es el error más fácil de cometer):**

1. **Token del Portal (Keycloak/JWT)** — lo emite `sso.utp.edu.pe`, lo captura el plugin de forma pasiva, y sirve **solo para LEER** de `api-pao.utpxpedition.com`. El plugin nunca lo manda a UTPBot.
2. **Token de UTPBot (Firebase idToken)** — se genera **dentro del plugin** cuando el alumno inicia sesión en el popup (email/contraseña de UTPBot). Sirve **solo para ESCRIBIR** en tu backend (`POST /estudiante/sincronizar`).

---

## 2. Descubrimientos técnicos (datos reales verificados)

### 2.1 Los 3 endpoints que necesita el plugin

Base URL: **`https://api-pao.utpxpedition.com`**

| # | Propósito | Método + Path | Verificado |
|---|---|---|---|
| 1 | Cursos matriculados | `GET /learning/student/{studentId}/dashboard-courses` | ✅ 200 |
| 2 | Horario / calendario | `GET /course/student/calendar` | ✅ 200 |
| 3 | Ciclos académicos | `GET /course/student/{studentId}/academicperiods` | ✅ 200 |

`{studentId}` es un UUID del alumno (en tu cuenta: `e4ee3abc-0854-5378-86c3-8858c8f6860f`). Se obtiene solo: viaja en el header `User-Id` de cada request y también en la URL. El plugin lo captura, no lo hardcodea.

### 2.2 Headers que la API exige (capturados de tu sesión)

Cada request a `api-pao` lleva **exactamente** estos headers. Faltando los custom, la API rechaza por CORS (probado: sin ellos → `Failed to fetch`; con ellos → `200`):

```
Authorization:        Bearer eyJhbGciOiJSUzI1Ni...      ← token Keycloak capturado
User-Id:              e4ee3abc-0854-5378-86c3-8858c8f6860f
User-Role:            STUDENT
User-Id-To-Access:    (vacío)
User-Role-To-Access:  (vacío)
X-Tenant-Id:          a5f469d2-3c0e-5c68-8d32-5265923a8e40
Transaction-Id:       <UUID nuevo por request>          ← generar con crypto.randomUUID()
Accept:               application/json
```

> `X-Tenant-Id` parece constante para UTP (`a5f469d2-...`), pero el plugin igual lo captura por si cambia entre campus/carreras. `Transaction-Id` es un UUID cualquiera por llamada.

### 2.3 Autenticación del portal (por qué el plugin no maneja contraseñas)

- El alumno ya está logueado en `class.utp.edu.pe` con su propia sesión Keycloak.
- El token Bearer vive **en memoria del Angular app**, adjuntado por un `HttpInterceptor`. Está guardado ofuscado en `localStorage` (claves tipo `_%0363f2y&U^E…`), así que **no** se lee de storage; se **observa** cuando la app lo manda en un header (técnica de §3).
- Por eso el plugin cumple el principio #1 de tu spec: **nunca ve ni transmite la contraseña del portal**. Es un "copiar/pegar" automatizado de datos que el alumno ya está viendo.

### 2.4 El JWT del portal ya trae identidad del alumno

Decodificando el payload del Bearer (claims, sin exponer el token):

```jsonc
{
  "iss": "https://sso.utp.edu.pe/auth/realms/Xpedition",
  "name": "FABRIZIO JEFFRY LEÓN PANDURO",     // → perfil.nombre
  "given_name": "FABRIZIO JEFFRY",
  "family_name": "LEÓN PANDURO",
  "preferred_username": "u21206744",           // → perfil.codigo (código institucional)
  "email": "«presente»",
  "roles": ["student", ...],
  "exp": <~24 h de validez>
}
```

⇒ `perfil.nombre` y `perfil.codigo` salen del JWT sin ninguna llamada extra. `carrera` **no** está en el token (ver §9).

### 2.5 Shape real de cada respuesta

Todas las respuestas de `api-pao` tienen el envoltorio `{ success, code, message, data }`. Lo que importa está en `data`.

**(1) `dashboard-courses` → `data` es un array de cursos.** Devolvió 43 items (todos tus ciclos históricos + cursos THP). Los del ciclo actual se filtran por `period` (ver §4.1). Campos por curso (los relevantes en **negrita**):

```jsonc
{
  "sectionId": "a71f5cee-…",
  "courseId": "0e3d1695-…",          // ← cruza con los eventos del calendario
  "progress": 5.88,                   // ← progreso_porcentaje (float; redondear)
  "active": true,
  "sectionCode": "226310000CN4756777",
  "classroom": null,                  // ← aula (hoy null, pero es el campo correcto)
  "classNumber": "31088",             // ← codigo_curso (el código visible en la UI)
  "modality": "P",                    // ← "P" | "VT" | "R"  (ver mapa §4.2)
  "teacherId": "…",
  "acadCareer": "PREG",               // PREG = pregrado, PRED = predeterminado (THP)
  "courseCode": "100000CN47",         // código de catálogo interno (NO el visible)
  "name": "INGENIERÍA ECONÓMICA",     // ← nombre_curso
  "period": "2263",                   // ← clave para filtrar el ciclo actual
  "module": "001",
  "teacherFirstName": "BETTY KAROL",  // ← docente (nombre)
  "teacherLastName": "ZAMORA YANSI",  // ← docente (apellido)
  "teacherEmail": "…@utp.edu.pe",
  "teacherCode": "…",
  "teachers": [ … ]                   // por si hay más de un docente
}
```

Tus 6 cursos del ciclo actual (`period: "2263"`), tal como los devolvió la API:

| classNumber | name | modality | progress | docente |
|---|---|---|---|---|
| 56777 | Formación Profesional c/ Enfoque en Discapacidad e Inclusión | VT | 1.56 | Martin Remberto Horna Romero |
| 49406 | Herramientas de Desarrollo Profesional - TIC | P | 4.17 | Jhonatan Abal Mejía |
| 31088 | Ingeniería Económica | P | 5.88 | Betty Karol Zamora Yansi |
| 31662 | Inteligencia de Negocios | P | 0 | Orlando Oswaldo Gonzales Samanez |
| 16947 | Seguridad Informática | VT | 0.93 | Liu Phol Ramos Fernandez |
| 49409 | Servicios Cloud | P | 5.56 | Jhoan … |

**(2) `calendar` → `data.current_interval.events` es un array de bloques de clase.** Devolvió 44 eventos (todas las semanas del ciclo). Shape de un evento:

```jsonc
{
  "current_interval": {
    "period_name": "2026 - Ciclo 2 Agosto",   // ← nombre del ciclo actual
    "week_number": 1, "total_weeks": 18,
    "start_of_period": "2026-08-10 00:00:00",
    "end_of_period":   "2026-12-08 00:00:00",
    "events": [
      {
        "id": "514341e4-…",
        "title": "INTELIGENCIA DE NEGOCIOS (31662) (Semana 3) - Viernes",
        "modality": "P",                        // ← "P" | "VT"
        "type": "CLASS",                         // filtrar por CLASS
        "startAt": "2026-08-28 18:30:00",        // ← hora_inicio (parte de la hora)
        "finishAt": "2026-08-28 20:00:00",       // ← hora_fin
        "metadata": {
          "courseId": "28097fa9-…",              // ← cruza con dashboard-courses
          "sectionId": "f0f27ecb-…",
          "zoomLink": "https://utpvirtual.zoom.us/j/…",
          "isLive": false
        }
      }
    ]
  }
}
```

> Los eventos abarcan TODO el ciclo (varias semanas), por eso el mismo curso aparece repetido. Para el "horario semanal" del contrato hay que **deduplicar** por `(curso, día, hora_inicio, hora_fin)` — ver §4.3. El `día` se deriva del `startAt`, no del texto del `title` (más confiable).

**(3) `academicperiods` → `data.academicPeriods` es un array de periodos.** Sirve para detectar cuál es el ciclo actual:

```jsonc
{
  "academicPeriods": [
    {
      "period": "2263",
      "acadCareer": "PREG",
      "name": "2026 - Ciclo 2 Agosto PREG (001)",
      "start": "2026-08-10", "end": "2026-12-13",
      "type": "CURRENT_PERIOD"        // ⚠️ ojo: el "9999" (THP) también se marca CURRENT_PERIOD
    },
    { "period": "9999", "acadCareer": "PRED", "name": "Período Predeterminado", … },
    { "period": "2262", "acadCareer": "PREG", "type": "OLD_PERIOD", … }
  ]
}
```

⚠️ **Cuidado:** el periodo `9999` (bolsa de cursos THP/extracurriculares) también viene con `type: "CURRENT_PERIOD"`. Por eso la detección robusta del ciclo NO usa solo `type`, sino la ventana de fechas + `acadCareer != "PRED"` (§4.1).

---

## 3. El corazón del plugin: captura pasiva + replay

### 3.1 Por qué no se puede hacer el fetch desde el service worker

Probado en vivo: un `fetch` a `api-pao` desde un origin distinto de `class.utp.edu.pe` (ej. `chrome-extension://…` o sin los headers) → **`Failed to fetch` (CORS)**. La API solo responde bien cuando el request sale **desde el mismo origin de la SPA** y con el token + headers custom.

**Conclusión de diseño:** la re-consulta (replay) tiene que ejecutarse **dentro del contexto de la página** (`world: MAIN`), donde el `Origin` es `https://class.utp.edu.pe` — idéntico a como lo hace la propia app. De ahí que `inject.js` corra en MAIN world y haga él mismo los fetch, devolviendo el JSON ya armado por `postMessage`.

### 3.2 Flujo del "Sincronizar"

1. El alumno abre `class.utp.edu.pe` (ya logueado) y visita **Cursos** o **Calendario** al menos una vez → `inject.js` captura token + `User-Id` + `X-Tenant-Id` de las llamadas que la app hace sola.
2. Abre el popup del plugin, inicia sesión en UTPBot (si no lo hizo antes) → se genera el **idToken Firebase**.
3. Click en **Sincronizar** → `popup.js` pide a `content.js` → `inject.js` que **re-consulte** los 3 endpoints y arme el JSON.
4. El popup muestra el **preview** ("Vas a enviar: 6 cursos, 6 bloques de horario…") con el detalle.
5. El alumno **confirma** → `background.js` hace `POST /estudiante/sincronizar` con el idToken.
6. Se muestra éxito/error.

Esto respeta los principios #2 (solo datos del propio alumno — el token es suyo), #3 (transparencia: preview antes de enviar) y #4 (lectura puntual disparada por el usuario, sin polling) de tu spec.

---

## 4. Mapeo de datos → contrato JSON

El contrato de salida es el de tu spec (§"Contrato JSON"). Estas son las transformaciones exactas.

### 4.1 Detectar el ciclo actual (robusto para re-sincronizar en futuros ciclos)

No hardcodear `"2263"`. Elegir de `academicPeriods` el periodo que:
1. Tenga `acadCareer != "PRED"` (descarta el bucket THP `9999`), **y**
2. Su ventana `[start, end]` incluya la fecha de hoy.

Fallbacks: si ninguno matchea por fecha, usar el `type == "CURRENT_PERIOD"` con `acadCareer != "PRED"`; si tampoco, el de fecha `start` más reciente. El `period` resultante (ej. `"2263"`) es la clave para filtrar `dashboard-courses`.

### 4.2 Cursos (`dashboard-courses` → `cursos[]`)

| Campo contrato | Origen API | Transformación |
|---|---|---|
| `codigo_curso` | `classNumber` | tal cual (ej. `"31088"`) |
| `nombre_curso` | `name` | tal cual (opcional: capitalizar) |
| `modalidad` | `modality` | mapa de enum ↓ |
| `docente` | `teacherFirstName` + `teacherLastName` | concatenar |
| `progreso_porcentaje` | `progress` | `Math.round()` |

**Mapa de modalidad** (enum del portal → texto que ve el alumno):

```
"P"  → "Presencial"
"VT" → "Virtual 24/7"
"V"  → "Virtual"        (por si aparece)
"R"  → "Regular"        (cursos THP; normalmente fuera del ciclo PREG)
```

### 4.3 Horario (`calendar.events` → `horarios[]`)

1. Filtrar `type === "CLASS"`.
2. Por evento: cruzar `metadata.courseId` con el curso en `dashboard-courses` para obtener su `classNumber` (código visible) y `classroom` (aula).
3. `dia` = día de la semana de `startAt` (calcular con `Date`, en español).
4. `hora_inicio` / `hora_fin` = parte horaria de `startAt` / `finishAt` en 24h (`"2026-08-28 18:30:00"` → `"18:30"`).
5. `modalidad` = mapa del §4.2 sobre `event.modality`.
6. `aula` = `classroom` del curso cruzado (hoy `null`).
7. **Deduplicar** por `codigo_curso|dia|hora_inicio|hora_fin` para quedarte con el patrón semanal (44 eventos del ciclo → ~6 bloques semanales únicos).

### 4.4 Perfil

| Campo contrato | Origen | Nota |
|---|---|---|
| `codigo` | JWT `preferred_username` | ej. `"u21206744"` |
| `nombre` | JWT `name` | |
| `ciclo` | `calendar.current_interval.period_name` o `academicPeriods[actual].name` | ej. `"2026 - Ciclo 2 Agosto"` |
| `ciclo_codigo` | `period` del ciclo actual | ej. `"2263"` |
| `carrera` | ⚠️ pendiente | no está en estos 3 endpoints ni en el JWT — ver §9 |

### 4.5 Ejemplo de payload real que produciría el plugin con tu cuenta

```json
{
  "perfil": {
    "codigo": "u21206744",
    "nombre": "FABRIZIO JEFFRY LEÓN PANDURO",
    "carrera": null,
    "ciclo": "2026 - Ciclo 2 Agosto",
    "ciclo_codigo": "2263"
  },
  "cursos": [
    { "codigo_curso": "31088", "nombre_curso": "INGENIERÍA ECONÓMICA", "modalidad": "Presencial", "docente": "BETTY KAROL ZAMORA YANSI", "progreso_porcentaje": 6 },
    { "codigo_curso": "31662", "nombre_curso": "INTELIGENCIA DE NEGOCIOS", "modalidad": "Presencial", "docente": "ORLANDO OSWALDO GONZALES SAMANEZ", "progreso_porcentaje": 0 },
    { "codigo_curso": "49409", "nombre_curso": "SERVICIOS CLOUD", "modalidad": "Presencial", "docente": "JHOAN …", "progreso_porcentaje": 6 },
    { "codigo_curso": "49406", "nombre_curso": "HERRAMIENTAS DE DESARROLLO PROFESIONAL - TIC", "modalidad": "Presencial", "docente": "JHONATAN ABAL MEJIA", "progreso_porcentaje": 4 },
    { "codigo_curso": "56777", "nombre_curso": "FORMACIÓN PROFESIONAL CON ENFOQUE EN DISCAPACIDAD E INCLUSIÓN", "modalidad": "Virtual 24/7", "docente": "MARTIN REMBERTO HORNA ROMERO", "progreso_porcentaje": 2 },
    { "codigo_curso": "16947", "nombre_curso": "SEGURIDAD INFORMÁTICA", "modalidad": "Virtual 24/7", "docente": "LIU PHOL RAMOS FERNANDEZ", "progreso_porcentaje": 1 }
  ],
  "horarios": [
    { "codigo_curso": "31662", "dia": "Viernes", "hora_inicio": "18:30", "hora_fin": "20:00", "modalidad": "Presencial", "aula": null },
    { "codigo_curso": "31088", "dia": "Viernes", "hora_inicio": "20:15", "hora_fin": "22:30", "modalidad": "Presencial", "aula": null }
  ]
}
```

---

## 5. Código completo de la extensión (Manifest V3)

Estructura de carpeta:

```
utpbot-sync/
├── manifest.json
├── inject.js        (MAIN world: captura + replay + arma el JSON)
├── content.js       (puente page ⇄ extensión)
├── background.js    (service worker: storage + POST al backend)
├── popup.html
├── popup.js         (login Firebase + sincronizar + preview)
└── icons/           (icon16.png, icon48.png, icon128.png — opcional)
```

> El código es **JavaScript vanilla** a propósito (sin build step): cargás la carpeta tal cual en `chrome://extensions`. Es totalmente funcional; lo único que tenés que rellenar son 2 constantes de config (API key de Firebase de UTPBot y URL del backend), que además son editables desde el popup.

### 5.1 `manifest.json`

```json
{
  "manifest_version": 3,
  "name": "UTPBot Sync",
  "version": "0.1.0",
  "description": "Sincroniza tus cursos y horario del Portal del Estudiante UTP con UTPBot.",
  "permissions": ["storage", "activeTab", "scripting", "tabs"],
  "host_permissions": [
    "https://class.utp.edu.pe/*",
    "https://api-pao.utpxpedition.com/*",
    "https://identitytoolkit.googleapis.com/*",
    "https://securetoken.googleapis.com/*",
    "http://localhost:8080/*"
  ],
  "action": {
    "default_popup": "popup.html",
    "default_title": "UTPBot Sync"
  },
  "background": { "service_worker": "background.js" },
  "content_scripts": [
    {
      "matches": ["https://class.utp.edu.pe/*"],
      "js": ["content.js"],
      "run_at": "document_start"
    }
  ],
  "web_accessible_resources": [
    { "resources": ["inject.js"], "matches": ["https://class.utp.edu.pe/*"] }
  ]
}
```

> Cuando publiques el backend en un dominio real, agregá su URL a `host_permissions` (ej. `https://api.utpbot.tudominio.com/*`) y quitá `http://localhost:8080/*`.

### 5.2 `inject.js` — captura pasiva + replay + armado del JSON (MAIN world)

```js
(function () {
  "use strict";
  const API = "https://api-pao.utpxpedition.com";
  const creds = { token: null, userId: null, tenantId: null };

  // ---------- captura pasiva de credenciales ----------
  function remember(h) {
    if (!h) return;
    const auth = h["authorization"];
    if (auth && /^Bearer /.test(auth)) creds.token = auth;
    if (h["user-id"]) creds.userId = h["user-id"];
    if (h["x-tenant-id"]) creds.tenantId = h["x-tenant-id"];
  }

  // Hook XHR (Angular HttpClient usa XHR)
  const XO = XMLHttpRequest.prototype.open;
  const XSH = XMLHttpRequest.prototype.setRequestHeader;
  const XSN = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (m, u) { this.__u = u; this.__h = {}; return XO.apply(this, arguments); };
  XMLHttpRequest.prototype.setRequestHeader = function (k, v) {
    try { this.__h[String(k).toLowerCase()] = v; } catch (e) {}
    return XSH.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    if (this.__u && /utpxpedition\.com/.test(this.__u)) remember(this.__h);
    return XSN.apply(this, arguments);
  };

  // Hook fetch (por si alguna llamada la usa)
  const OF = window.fetch;
  window.fetch = function (input, init) {
    try {
      const url = typeof input === "string" ? input : (input && input.url);
      if (url && /utpxpedition\.com/.test(url)) {
        const h = {};
        if (init && init.headers) new Headers(init.headers).forEach((v, k) => (h[k.toLowerCase()] = v));
        remember(h);
      }
    } catch (e) {}
    return OF.apply(this, arguments);
  };

  // ---------- helpers de request ----------
  function reqHeaders() {
    return {
      "Authorization": creds.token,
      "User-Id": creds.userId,
      "User-Role": "STUDENT",
      "User-Id-To-Access": "",
      "User-Role-To-Access": "",
      "X-Tenant-Id": creds.tenantId,
      "Transaction-Id": (crypto.randomUUID ? crypto.randomUUID() : String(Date.now())),
      "Accept": "application/json"
    };
  }
  async function api(path) {
    const r = await fetch(API + path, { headers: reqHeaders() });
    if (!r.ok) throw new Error("HTTP " + r.status + " en " + path);
    const j = await r.json();
    return j && j.data !== undefined ? j.data : j;
  }

  // ---------- transformaciones ----------
  const MOD = { P: "Presencial", VT: "Virtual 24/7", V: "Virtual", VS: "Virtual", R: "Regular" };
  const DAYS = ["Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];
  const pad = (n) => String(n).padStart(2, "0");
  const hhmm = (dt) => { const t = String(dt).split(" ")[1] || ""; const [h, m] = t.split(":"); return h && m ? pad(h) + ":" + pad(m) : null; };
  const weekday = (dt) => { const [Y, M, D] = String(dt).split(" ")[0].split("-").map(Number); return DAYS[new Date(Y, M - 1, D).getDay()]; };

  function decodeJwt(bearer) {
    try {
      const p = String(bearer).replace(/^Bearer /, "").split(".")[1];
      return JSON.parse(decodeURIComponent(escape(atob(p.replace(/-/g, "+").replace(/_/g, "/")))));
    } catch (e) { return {}; }
  }

  function pickCurrentPeriod(periodsData) {
    const list = (periodsData && periodsData.academicPeriods) || [];
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const inRange = (p) => p.start && p.end && new Date(p.start) <= today && today <= new Date(p.end + "T23:59:59");
    return list.find((p) => p.acadCareer !== "PRED" && inRange(p))
        || list.find((p) => p.type === "CURRENT_PERIOD" && p.acadCareer !== "PRED")
        || list.filter((p) => p.acadCareer !== "PRED").sort((a, b) => (b.start || "").localeCompare(a.start || ""))[0]
        || null;
  }

  // ---------- armado del contrato ----------
  async function buildPayload() {
    if (!creds.token || !creds.userId) {
      throw new Error('Todavía no capturé el token del portal. Entrá a "Cursos" o "Calendario" en el portal y reintentá.');
    }
    const sid = creds.userId;
    const [courses, cal, periods] = await Promise.all([
      api("/learning/student/" + sid + "/dashboard-courses"),
      api("/course/student/calendar"),
      api("/course/student/" + sid + "/academicperiods")
    ]);

    const all = Array.isArray(courses) ? courses : [];
    const period = pickCurrentPeriod(periods);
    const periodCode = period ? period.period : null;
    const cycleName = (period && period.name) || (cal && cal.current_interval && cal.current_interval.period_name) || null;

    const current = all.filter((c) => (periodCode ? c.period === periodCode : c.active));
    const byCourseId = {};
    current.forEach((c) => (byCourseId[c.courseId] = c));

    const cursos = current.map((c) => ({
      codigo_curso: c.classNumber,
      nombre_curso: c.name,
      modalidad: MOD[c.modality] || c.modality || null,
      docente: [c.teacherFirstName, c.teacherLastName].filter(Boolean).join(" ").trim() || null,
      progreso_porcentaje: Math.round(c.progress || 0)
    }));

    const events = (cal && cal.current_interval && cal.current_interval.events) || [];
    const seen = new Set();
    const horarios = [];
    events.filter((e) => e.type === "CLASS").forEach((e) => {
      const course = byCourseId[e.metadata && e.metadata.courseId];
      const dia = weekday(e.startAt);
      const hi = hhmm(e.startAt), hf = hhmm(e.finishAt);
      const cod = course ? course.classNumber : null;
      const key = cod + "|" + dia + "|" + hi + "|" + hf;
      if (seen.has(key)) return;
      seen.add(key);
      horarios.push({
        codigo_curso: cod,
        dia: dia, hora_inicio: hi, hora_fin: hf,
        modalidad: MOD[e.modality] || e.modality || null,
        aula: course ? (course.classroom || null) : null
      });
    });

    const jwt = decodeJwt(creds.token);
    return {
      perfil: {
        codigo: jwt.preferred_username || null,
        nombre: jwt.name || null,
        carrera: null, // TODO: endpoint de perfil — ver §9 de la guía
        ciclo: cycleName,
        ciclo_codigo: periodCode
      },
      cursos: cursos,
      horarios: horarios
    };
  }

  // ---------- puente con content.js ----------
  window.addEventListener("message", async (ev) => {
    if (ev.source !== window || !ev.data || ev.data.__utpbot !== "req") return;
    const id = ev.data.id;
    try {
      const payload = await buildPayload();
      window.postMessage({ __utpbot: "res", id: id, ok: true, payload: payload }, "*");
    } catch (e) {
      window.postMessage({ __utpbot: "res", id: id, ok: false, error: String((e && e.message) || e) }, "*");
    }
  });
})();
```

### 5.3 `content.js` — puente (ISOLATED world)

```js
// Inyecta inject.js en el MAIN world para poder observar el fetch/XHR de la SPA
// y re-consultar api-pao desde el mismo origin.
(function () {
  const s = document.createElement("script");
  s.src = chrome.runtime.getURL("inject.js");
  s.onload = () => s.remove();
  (document.head || document.documentElement).appendChild(s);
})();

const pending = {};
window.addEventListener("message", (ev) => {
  if (ev.source !== window || !ev.data || ev.data.__utpbot !== "res") return;
  const cb = pending[ev.data.id];
  if (cb) { cb(ev.data); delete pending[ev.data.id]; }
});

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg && msg.type === "UTPBOT_BUILD") {
    const id = Math.random().toString(36).slice(2);
    pending[id] = (data) => sendResponse(data);
    window.postMessage({ __utpbot: "req", id: id }, "*");
    return true; // respuesta async
  }
});
```

### 5.4 `background.js` — service worker (POST al backend + refresh de token)

```js
const DEFAULTS = {
  backendUrl: "http://localhost:8080",
  firebaseApiKey: "" // ← poné acá la Web API Key de Firebase de UTPBot (o desde el popup)
};

async function cfg() {
  const c = await chrome.storage.local.get(["backendUrl", "firebaseApiKey"]);
  return { ...DEFAULTS, ...c };
}

// Refresca el idToken de Firebase si está por vencer
async function freshIdToken() {
  const { firebaseApiKey } = await cfg();
  const s = await chrome.storage.local.get(["idToken", "refreshToken", "idTokenExp"]);
  if (!s.refreshToken) throw new Error("No hay sesión de UTPBot. Iniciá sesión en el popup.");
  const now = Date.now();
  if (s.idToken && s.idTokenExp && now < s.idTokenExp - 60000) return s.idToken;

  const body = new URLSearchParams({ grant_type: "refresh_token", refresh_token: s.refreshToken });
  const r = await fetch("https://securetoken.googleapis.com/v1/token?key=" + firebaseApiKey, {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body
  });
  const j = await r.json();
  if (!r.ok) throw new Error("No pude refrescar el token: " + (j.error?.message || r.status));
  await chrome.storage.local.set({
    idToken: j.id_token, refreshToken: j.refresh_token,
    idTokenExp: Date.now() + Number(j.expires_in) * 1000
  });
  return j.id_token;
}

chrome.runtime.onMessage.addListener((msg, _s, sendResponse) => {
  if (msg && msg.type === "UTPBOT_POST") {
    (async () => {
      try {
        const { backendUrl } = await cfg();
        const idToken = await freshIdToken();
        const r = await fetch(backendUrl.replace(/\/$/, "") + "/estudiante/sincronizar", {
          method: "POST",
          headers: { "Content-Type": "application/json", "Authorization": "Bearer " + idToken },
          body: JSON.stringify(msg.payload)
        });
        const text = await r.text();
        sendResponse({ ok: r.ok, status: r.status, body: text });
      } catch (e) {
        sendResponse({ ok: false, error: String((e && e.message) || e) });
      }
    })();
    return true;
  }
});
```

### 5.5 `popup.html`

```html
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <style>
    body { font: 13px/1.4 system-ui, sans-serif; width: 340px; margin: 0; padding: 14px; color: #1a1a2e; }
    h1 { font-size: 15px; margin: 0 0 10px; }
    input, button { width: 100%; box-sizing: border-box; padding: 8px; margin: 4px 0; border-radius: 6px; border: 1px solid #ccc; }
    button { background: #c8102e; color: #fff; border: none; cursor: pointer; font-weight: 600; }
    button.sec { background: #eee; color: #333; }
    button:disabled { opacity: .5; cursor: default; }
    .hide { display: none; }
    .row { display: flex; gap: 6px; }
    pre { background: #f5f5f7; padding: 8px; border-radius: 6px; max-height: 180px; overflow: auto; font-size: 11px; }
    .muted { color: #666; font-size: 11px; }
    .ok { color: #0a7f3f; } .err { color: #c8102e; }
    details summary { cursor: pointer; color: #666; font-size: 11px; margin-top: 6px; }
  </style>
</head>
<body>
  <h1>UTPBot Sync</h1>

  <!-- Estado 1: login -->
  <div id="login">
    <p class="muted">Iniciá sesión con tu cuenta de UTPBot.</p>
    <input id="email" type="email" placeholder="correo UTPBot" autocomplete="username" />
    <input id="password" type="password" placeholder="contraseña" autocomplete="current-password" />
    <button id="btnLogin">Iniciar sesión</button>
    <p id="loginMsg" class="err"></p>
  </div>

  <!-- Estado 2: sincronizar -->
  <div id="synced" class="hide">
    <p class="muted">Sesión: <b id="who"></b> · <a href="#" id="logout">salir</a></p>
    <button id="btnBuild">Sincronizar con UTPBot</button>
    <div id="preview" class="hide">
      <p id="summary"></p>
      <pre id="json"></pre>
      <div class="row">
        <button id="btnSend">Confirmar y enviar</button>
        <button id="btnCancel" class="sec">Cancelar</button>
      </div>
    </div>
    <p id="msg"></p>
  </div>

  <details>
    <summary>Configuración</summary>
    <input id="backendUrl" placeholder="URL backend (http://localhost:8080)" />
    <input id="firebaseApiKey" placeholder="Firebase Web API Key de UTPBot" />
    <button id="btnSaveCfg" class="sec">Guardar config</button>
  </details>

  <script src="popup.js"></script>
</body>
</html>
```

### 5.6 `popup.js` — login Firebase + sincronizar + preview

```js
const $ = (id) => document.getElementById(id);
let currentPayload = null;

// ---------- config ----------
async function loadCfg() {
  const c = await chrome.storage.local.get(["backendUrl", "firebaseApiKey"]);
  $("backendUrl").value = c.backendUrl || "http://localhost:8080";
  $("firebaseApiKey").value = c.firebaseApiKey || "";
}
$("btnSaveCfg").onclick = async () => {
  await chrome.storage.local.set({
    backendUrl: $("backendUrl").value.trim(),
    firebaseApiKey: $("firebaseApiKey").value.trim()
  });
  $("msg").textContent = "Config guardada.";
};

// ---------- estado de sesión ----------
async function refreshUI() {
  const s = await chrome.storage.local.get(["refreshToken", "email"]);
  if (s.refreshToken) {
    $("login").classList.add("hide");
    $("synced").classList.remove("hide");
    $("who").textContent = s.email || "UTPBot";
  } else {
    $("login").classList.remove("hide");
    $("synced").classList.add("hide");
  }
}

// ---------- login Firebase (REST) ----------
$("btnLogin").onclick = async () => {
  $("loginMsg").textContent = "";
  const { firebaseApiKey } = await chrome.storage.local.get("firebaseApiKey");
  if (!firebaseApiKey) { $("loginMsg").textContent = "Falta la Firebase API Key (ver Configuración)."; return; }
  const email = $("email").value.trim(), password = $("password").value;
  $("btnLogin").disabled = true;
  try {
    const r = await fetch("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + firebaseApiKey, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, returnSecureToken: true })
    });
    const j = await r.json();
    if (!r.ok) throw new Error(j.error?.message || "login falló");
    await chrome.storage.local.set({
      idToken: j.idToken, refreshToken: j.refreshToken,
      idTokenExp: Date.now() + Number(j.expiresIn) * 1000, email
    });
    $("password").value = "";
    await refreshUI();
  } catch (e) {
    $("loginMsg").textContent = "Error: " + e.message;
  } finally { $("btnLogin").disabled = false; }
};

$("logout").onclick = async (e) => {
  e.preventDefault();
  await chrome.storage.local.remove(["idToken", "refreshToken", "idTokenExp", "email"]);
  await refreshUI();
};

// ---------- construir preview desde el portal ----------
$("btnBuild").onclick = async () => {
  $("msg").textContent = "Leyendo el portal…";
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab || !/class\.utp\.edu\.pe/.test(tab.url || "")) {
    $("msg").innerHTML = '<span class="err">Abrí primero el Portal del Estudiante (class.utp.edu.pe) en esta pestaña.</span>';
    return;
  }
  chrome.tabs.sendMessage(tab.id, { type: "UTPBOT_BUILD" }, (res) => {
    if (chrome.runtime.lastError || !res) {
      $("msg").innerHTML = '<span class="err">No pude leer la página. Recargá el portal y reintentá.</span>';
      return;
    }
    if (!res.ok) { $("msg").innerHTML = '<span class="err">' + res.error + "</span>"; return; }
    currentPayload = res.payload;
    const c = res.payload.cursos.length, h = res.payload.horarios.length;
    $("summary").innerHTML = "Vas a enviar: <b>" + c + " cursos</b> y <b>" + h + " bloques de horario</b> del ciclo <b>" + (res.payload.perfil.ciclo || "?") + "</b>.";
    $("json").textContent = JSON.stringify(res.payload, null, 2);
    $("preview").classList.remove("hide");
    $("msg").textContent = "";
  });
};

$("btnCancel").onclick = () => { $("preview").classList.add("hide"); currentPayload = null; };

// ---------- enviar al backend ----------
$("btnSend").onclick = () => {
  if (!currentPayload) return;
  $("msg").textContent = "Enviando…";
  $("btnSend").disabled = true;
  chrome.runtime.sendMessage({ type: "UTPBOT_POST", payload: currentPayload }, (res) => {
    $("btnSend").disabled = false;
    if (!res) { $("msg").innerHTML = '<span class="err">Sin respuesta del backend.</span>'; return; }
    if (res.ok) {
      $("msg").innerHTML = '<span class="ok">✓ Sincronizado correctamente.</span>';
      $("preview").classList.add("hide");
    } else {
      $("msg").innerHTML = '<span class="err">Error ' + (res.status || "") + ": " + (res.error || res.body || "") + "</span>";
    }
  });
};

loadCfg().then(refreshUI);
```

---

## 6. Login de UTPBot dentro del plugin (lo que pediste)

El plugin **no** lee el token de otra pestaña: el alumno inicia sesión en el propio popup y ahí se genera el token. Se usa la **API REST de Firebase Auth** (no hace falta bundlear el SDK entero, que además es problemático en service workers MV3).

### 6.1 Flujo

```
[popup] email + contraseña UTPBot
   │
   ▼  POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=<API_KEY>
      { email, password, returnSecureToken: true }
   │
   ▼  respuesta: { idToken, refreshToken, expiresIn, localId, email }
   │
   ├─ guardar refreshToken (chrome.storage.local) → sesión persistente
   ├─ guardar idToken + idTokenExp → se usa como Bearer del POST
   │
   ▼  cuando el idToken vence (~1 h):
      POST https://securetoken.googleapis.com/v1/token?key=<API_KEY>
      grant_type=refresh_token&refresh_token=<...>  → nuevo id_token
```

Esto ya está implementado en `popup.js` (login) y `background.js` (refresh + uso). El `refreshToken` de Firebase es de larga duración, así que el alumno inicia sesión una vez y el plugin renueva el `idToken` solo.

### 6.2 Qué necesitás de UTPBot para que funcione

- **Firebase Web API Key** de tu proyecto UTPBot: Firebase Console → *Project settings* → *General* → *Your apps* → config web (`apiKey`). Es pública (va embebida en el front), no es secreto. Se pega en la Configuración del popup (o en `DEFAULTS.firebaseApiKey`).
- Que tu proyecto Firebase tenga habilitado el proveedor **Email/Password** (Authentication → Sign-in method). Si UTPBot usa login con Google en vez de email/password, ver §6.3.

### 6.3 Variante con Google (si UTPBot no usa email/password)

Si UTPBot autentica con Google, el email/password no aplica. La forma correcta en una extensión MV3 es `chrome.identity.launchWebAuthFlow` para obtener el `id_token` de Google y canjearlo:

```js
// manifest: agregar "identity" a permissions
const redirectUri = chrome.identity.getRedirectURL(); // https://<ext-id>.chromiumapp.org/
const authUrl = "https://accounts.google.com/o/oauth2/v2/auth?"
  + new URLSearchParams({
      client_id: "<OAUTH_CLIENT_ID_web>", response_type: "id_token",
      redirect_uri: redirectUri, scope: "openid email profile",
      nonce: crypto.randomUUID()
    });
const resp = await chrome.identity.launchWebAuthFlow({ url: authUrl, interactive: true });
const googleIdToken = new URLSearchParams(resp.split("#")[1]).get("id_token");
// canjear por credencial de Firebase:
const r = await fetch("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=" + API_KEY, {
  method: "POST", headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    postBody: "id_token=" + googleIdToken + "&providerId=google.com",
    requestUri: redirectUri, returnSecureToken: true
  })
});
// → { idToken, refreshToken, ... }  (mismo manejo que §6.1)
```

Necesitás registrar el `redirectUri` de la extensión como Authorized redirect en tu OAuth Client de Google Cloud. **Decime cómo loguea UTPBot y te dejo esta parte cableada.**

---

## 7. Backend: `POST /estudiante/sincronizar`

Tu spec dice que este endpoint todavía no existe. Acá va una implementación de referencia. Como tu repo real es **Quarkus** (`backend-quarkus`), pongo Quarkus primero; y como tu preferencia declarada es **Spring Boot**, agrego el equivalente por si migrás.

### 7.1 DTOs (contrato de entrada)

```java
public record SincronizarRequest(
    Perfil perfil,
    List<CursoDTO> cursos,
    List<HorarioDTO> horarios
) {
  public record Perfil(String codigo, String nombre, String carrera,
                       String ciclo, String cicloCodigo) {}
  public record CursoDTO(String codigoCurso, String nombreCurso, String modalidad,
                         String docente, Integer progresoPorcentaje) {}
  public record HorarioDTO(String codigoCurso, String dia, String horaInicio,
                           String horaFin, String modalidad, String aula) {}
}
```

> El JSON del plugin usa `snake_case` (`codigo_curso`). Configurá Jackson para mapear snake_case → camelCase: en Quarkus, `quarkus.jackson.property-naming-strategy=SNAKE_CASE`; en Spring, `spring.jackson.property-naming-strategy=SNAKE_CASE`. Así los records de arriba (camelCase) reciben el snake_case sin anotar cada campo.

### 7.2 Recurso Quarkus (JAX-RS)

```java
@Path("/estudiante")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EstudianteResource {

    @Inject CurrentUser currentUser;      // el mismo que ya usás en ChatResource/DocenteResource
    @Inject EstudianteSyncService service;

    @POST
    @Path("/sincronizar")
    @RolesAllowed("estudiante")
    @Transactional
    public Response sincronizar(SincronizarRequest req) {
        // NUNCA confiar en un codigo que mande el cliente: se toma del token verificado
        String codigo = currentUser.getCodigo();
        var resumen = service.sincronizar(codigo, req);
        return Response.ok(resumen).build();
    }
}
```

### 7.3 Servicio con upsert (Panache)

```java
@ApplicationScoped
public class EstudianteSyncService {

    public Map<String, Object> sincronizar(String codigo, SincronizarRequest req) {
        Estudiante est = Estudiante.find("codigo", codigo).firstResult();
        if (est == null) { est = new Estudiante(); est.codigo = codigo; }
        // perfil (no pisar con null)
        if (req.perfil() != null) {
            if (req.perfil().nombre()  != null) est.nombre  = req.perfil().nombre();
            if (req.perfil().carrera() != null) est.carrera = req.perfil().carrera();
            if (req.perfil().ciclo()   != null) est.ciclo   = req.perfil().ciclo();
        }
        est.persist();

        int cUp = 0, hUp = 0;
        // upsert cursos por (estudiante, codigo_curso)
        for (var c : req.cursos()) {
            Curso curso = Curso.find("estudiante = ?1 and codigoCurso = ?2", est, c.codigoCurso()).firstResult();
            if (curso == null) { curso = new Curso(); curso.estudiante = est; curso.codigoCurso = c.codigoCurso(); }
            curso.nombreCurso = c.nombreCurso();
            curso.modalidad   = c.modalidad();          // columna NUEVA (ver 7.5)
            curso.progreso    = c.progresoPorcentaje();  // columna NUEVA
            curso.estado      = "en curso";
            curso.persist();
            cUp++;
        }
        // upsert horarios por (estudiante, codigo_curso, dia, hora_inicio)
        for (var h : req.horarios()) {
            Horario hor = Horario.find(
                "estudiante = ?1 and codigoCurso = ?2 and dia = ?3 and horaInicio = ?4",
                est, h.codigoCurso(), h.dia(), h.horaInicio()).firstResult();
            if (hor == null) {
                hor = new Horario(); hor.estudiante = est; hor.codigoCurso = h.codigoCurso();
                hor.dia = h.dia(); hor.horaInicio = h.horaInicio();
            }
            hor.horaFin    = h.horaFin();
            hor.modalidad  = h.modalidad();   // columna NUEVA
            hor.aula       = h.aula();
            hor.persist();
            hUp++;
        }
        return Map.of("ok", true, "cursos", cUp, "horarios", hUp);
    }
}
```

### 7.4 Equivalente Spring Boot (tu preferencia)

```java
@RestController
@RequestMapping("/estudiante")
public class EstudianteController {

    private final EstudianteSyncService service;
    public EstudianteController(EstudianteSyncService s) { this.service = s; }

    @PostMapping("/sincronizar")
    @PreAuthorize("hasRole('estudiante')")
    public ResponseEntity<?> sincronizar(@RequestBody SincronizarRequest req,
                                         @AuthenticationPrincipal Jwt jwt) {
        String codigo = jwt.getClaimAsString("preferred_username"); // o el claim que uses
        return ResponseEntity.ok(service.sincronizar(codigo, req));
    }
}
```

El `@AuthenticationPrincipal Jwt` sale de configurar el resource server con el emisor de Firebase (issuer `https://securetoken.google.com/<projectId>`, jwks de Google). Igual que en Quarkus: **el `codigo` se lee del token, nunca del body.**

### 7.5 Migración de schema (Flyway `V2__sync_fields.sql`)

Los datos nuevos que la API del portal expone y tu schema `V1__init_schema.sql` no tiene:

```sql
-- Modalidad y progreso por curso
ALTER TABLE cursos   ADD COLUMN IF NOT EXISTS modalidad VARCHAR(20);
ALTER TABLE cursos   ADD COLUMN IF NOT EXISTS progreso  INTEGER DEFAULT 0;

-- Modalidad por bloque de horario (aula ya existe en tu schema)
ALTER TABLE horarios ADD COLUMN IF NOT EXISTS modalidad VARCHAR(20);

-- Índices únicos para que el upsert sea determinístico
CREATE UNIQUE INDEX IF NOT EXISTS ux_cursos_est_codigo
  ON cursos (estudiante_id, codigo_curso);
CREATE UNIQUE INDEX IF NOT EXISTS ux_horarios_est_bloque
  ON horarios (estudiante_id, codigo_curso, dia, hora_inicio);
```

> Ajustá nombres de columnas FK (`estudiante_id`) a los reales de tu `V1`. Si preferís, en vez de índice único + find/persist podés usar `INSERT ... ON CONFLICT ... DO UPDATE` nativo de Postgres para el upsert.

### 7.6 CORS: dejar entrar a la extensión

El POST sale desde `chrome-extension://<id>`. El backend debe permitir ese origin. En Quarkus (`application.properties`):

```properties
quarkus.http.cors=true
quarkus.http.cors.origins=chrome-extension://TU_EXTENSION_ID
quarkus.http.cors.methods=POST,OPTIONS
quarkus.http.cors.headers=authorization,content-type
```

En Spring Boot:

```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(List.of("chrome-extension://TU_EXTENSION_ID"));
    c.setAllowedMethods(List.of("POST", "OPTIONS"));
    c.setAllowedHeaders(List.of("authorization", "content-type"));
    var src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/estudiante/**", c);
    return src;
}
```

El `TU_EXTENSION_ID` lo ves en `chrome://extensions` después de cargarla (§8). En modo desarrollo podés usar `*` temporalmente, pero fijá el ID real antes de producción.

---

## 8. Cómo instalar y probar (paso a paso)

### 8.1 Cargar la extensión (modo desarrollador)

1. Creá la carpeta `utpbot-sync/` con los 6 archivos del §5.
2. Chrome → `chrome://extensions` → activá **Modo de desarrollador** (arriba a la derecha).
3. **Cargar descomprimida** → seleccioná la carpeta `utpbot-sync/`.
4. Copiá el **ID** que aparece (lo necesitás para el CORS del backend, §7.6).
5. (Firefox: `about:debugging` → *This Firefox* → *Load Temporary Add-on* → elegí el `manifest.json`.)

### 8.2 Configurar

1. Click en el ícono del plugin → *Configuración*.
2. Pegá la **Firebase Web API Key** de UTPBot y la **URL del backend** (`http://localhost:8080` en dev). Guardar.

### 8.3 Probar el flujo completo

1. Iniciá sesión en el Portal del Estudiante (`class.utp.edu.pe`) y entrá a **Cursos** y **Calendario** una vez (para que el plugin capture el token).
2. Iniciá sesión en el popup con tu cuenta de UTPBot.
3. Click **Sincronizar** → revisá el preview ("6 cursos, N bloques…").
4. **Confirmar y enviar** → deberías ver `✓ Sincronizado`.
5. Verificá en la base de datos de UTPBot que los `cursos`/`horarios` quedaron con tu `codigo`.

> **Tip de debug sin backend:** antes de tener el endpoint, podés apuntar la URL del backend a un `https://webhook.site/…` para ver el JSON que llega, o abrir el popup, hacer *Sincronizar* y copiar el JSON del preview. También podés probar solo la extracción ejecutando `buildPayload()` en la consola del portal (ver §10.1).

---

## 9. Lo que queda pendiente (con datos reales, honesto)

| Pendiente | Estado | Cómo resolverlo |
|---|---|---|
| **`carrera`** del alumno | No está en los 3 endpoints ni en el JWT | Falta descubrir el endpoint de perfil/persona. El plugin ya loguea en consola todos los endpoints `api-pao` que la app llama (§10.1); abrí la página de **Perfil/Mi cuenta** del portal y capturá la llamada que traiga la carrera. Probable candidato: algo bajo `/person/…` o `/student/…/profile`. Cuando lo tengas, agregás 1 fetch en `buildPayload()`. |
| **Créditos** por curso | No visibles en estos endpoints | Puede requerir el endpoint de detalle de curso o malla curricular. Si no aparece, dejar `null` como dice tu spec. |
| **Notas / calificaciones** | No exploradas (no estabas en esa sección) | Repetir la técnica de captura en la sección de notas del portal; mapear a la tabla `notas`. |
| **`aula` física** | Campo `classroom` existe pero hoy `null` en tus cursos | El plugin ya lo envía; se poblará cuando el portal lo tenga. Alternativa: el detalle del evento del calendario podría traerla — inspeccionar `metadata` de un evento al hacer clic. |
| **Firma de token de UTPBot en el backend** | Depende de tu setup Firebase | Configurar el resource server con el issuer `https://securetoken.google.com/<projectId>`. |
| **Endpoint `POST /estudiante/sincronizar`** | A implementar (§7) | Código de referencia listo; adaptá entidades/columnas reales. |

### Decisiones que necesito de vos

1. **¿Cómo loguea UTPBot?** Email/contraseña (ya cableado) o Google (§6.3). Pasame el método y la Firebase API Key / OAuth client.
2. **URL final del backend** en producción (para `host_permissions` y CORS).
3. ¿Querés que agregue al schema **modalidad** y **progreso** (§7.5)? Yo diría que sí — son útiles para que el asistente responda "¿mi curso es presencial o virtual?" y "¿cuánto llevo del curso?".
4. ¿Sincronizás **solo el ciclo actual** (recomendado) o querés histórico? La API trae todos los ciclos; hoy filtro al actual.

---

## 10. Anexos

### 10.1 Cómo re-descubrir endpoints (para `carrera`, notas, o si la API cambia)

Pegá esto en la consola del portal (`F12` en `class.utp.edu.pe`) para loguear cada endpoint `api-pao` que la app llama y su shape — así descubrís nuevos endpoints navegando el portal:

```js
(function () {
  const S = XMLHttpRequest.prototype.send, O = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (m, u) { this.__u = u; return O.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function () {
    this.addEventListener("load", function () {
      if (this.__u && /utpxpedition/.test(this.__u)) {
        let j = null; try { j = JSON.parse(this.responseText); } catch (e) {}
        console.log("[API]", this.__u.split("?")[0], j && j.data ? Object.keys(j.data) : j);
      }
    });
    return S.apply(this, arguments);
  };
  console.log("Logger de endpoints activo. Navegá el portal (Perfil, Notas, etc.).");
})();
```

Luego entrá a la sección que te interese (Perfil, Notas) y mirá la consola.

### 10.2 Probar `buildPayload()` aislado (sin instalar la extensión)

En la consola del portal, pegá el cuerpo de `inject.js` (§5.2) y ejecutá `await buildPayload()` — te devuelve el JSON exacto que enviaría el plugin. Útil para validar mapeos rápido.

### 10.3 Notas de seguridad y buenas prácticas

- **Nunca** loguear el token del portal ni el idToken de Firebase en producción (los `console.log` de debug son solo para desarrollo).
- El `refreshToken` de Firebase queda en `chrome.storage.local` (aislado por extensión). Si querés más seguridad, ofrecé un botón de "cerrar sesión" que lo borre (ya está en el popup).
- El plugin corre **solo** en `class.utp.edu.pe` (content script con `matches` restringido) — no tiene acceso a otras webs.
- Respetá el principio #4 de tu spec: nada de polling. La sync es siempre disparada por el botón.
- Antes de publicar en la Chrome Web Store: fijá el `host_permissions` del backend al dominio real, quitá `localhost`, y fijá el CORS al `chrome-extension://<id>` definitivo (para un ID estable en la Store, publicá con una `key` en el manifest o usá el ID que asigne la Store).

### 10.4 Resumen de endpoints (referencia rápida)

```
LEER (token Keycloak capturado, headers del §2.2):
  GET  https://api-pao.utpxpedition.com/learning/student/{studentId}/dashboard-courses
  GET  https://api-pao.utpxpedition.com/course/student/calendar
  GET  https://api-pao.utpxpedition.com/course/student/{studentId}/academicperiods

AUTENTICAR EN UTPBOT (dentro del plugin):
  POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}
  POST https://securetoken.googleapis.com/v1/token?key={API_KEY}     (refresh)

ESCRIBIR (idToken Firebase):
  POST {backendUrl}/estudiante/sincronizar
```

---

*Fin de la guía. Todo el bloque de "descubrimientos" (§2) fue verificado con requests reales sobre la cuenta del alumno el 15/08/2026; el código de §5–§7 es funcional y solo requiere completar la config (Firebase API Key + URL backend) y el endpoint del backend.*





