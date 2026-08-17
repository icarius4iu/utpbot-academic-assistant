# UTPBot Backend (Quarkus) — Referencia de API para generar una colección de Postman

Este documento describe los contratos HTTP del backend Quarkus tal como existen hoy en
el código (rama de migración), para que otra IA (o una persona) genere una colección de
Postman a partir de él. **No es documentación de un servicio ya desplegado** — es la
referencia de lo que el código YA implementa, listo para probar en cuanto exista una
instancia corriendo con credenciales reales (ver "Requisitos para correr esto" abajo).

Plan de migración completo: `/home/codespace/.claude/plans/bright-doodling-twilight.md`

---

## Estado de implementación

| Endpoint | Estado | Notas |
|---|---|---|
| `GET /` | ✅ Implementado y probado en vivo | Info del servicio |
| `GET /health` | ✅ Implementado y probado en vivo | Healthcheck (usado por Railway) |
| `POST /auth/login` | ✅ Implementado y probado en vivo (estudiante, docente, admin) | Login con Firebase custom token |
| `POST /chat` | ✅ Implementado y probado en vivo (401/403 de auth); flujo con Gemini requiere `GEMINI_API_KEY` real | Chat con Gemini (function-calling, adjuntos) |
| `GET /admin/dashboard` | ✅ Implementado y probado en vivo con datos reales | Overview + 4 gráficos |
| `GET /admin/stats/*`, `/admin/faq-analytics` | ✅ Implementado y probado en vivo | |
| `GET /docente/seccion/{codigo}`, `/docente/resumen/{codigo}` | ✅ Implementado y probado en vivo (incluye 403 al consultar otro docente) | |
| `POST /telegram/webhook`, `/telegram/setup-webhook`, `GET /telegram/status` | ✅ Implementado y probado en vivo | |
| `POST /transcribe` | ✅ Implementado (401 sin token probado en vivo; flujo completo requiere `GEMINI_API_KEY` real) | |
| `/estudio/*` (16 endpoints) | ✅ Implementado y probado en vivo con IA real | Módulo de estudio: sílabo→ruta, materiales→cuestionarios/resúmenes, metas y racha |

**Todos los endpoints del backend ya están implementados.** Solo falta lo que requiere
credenciales reales de terceros para probarse de punta a punta: `GEMINI_API_KEY` (para
que `/chat` y `/transcribe` generen respuestas reales) y un bot de Telegram real (para
que `/telegram/setup-webhook` registre un webhook de verdad).

> **Se corrieron dos rondas de pruebas end-to-end reales** (Postgres descartable vía
> Quarkus Dev Services + Docker): una con credenciales de Firebase de prueba (solo
> valida hasta el minteo del custom token), y una segunda con el **emulador local de
> Firebase Auth** (`firebase emulators:start --only auth`), que sí permite canjear el
> custom token por un ID token real y probar los endpoints protegidos de punta a
> punta — login (los 3 roles), `/admin/dashboard` con datos reales sembrados a mano,
> `/admin/faq-analytics`, `/docente/resumen` con el chequeo de "solo tu propio
> código" (403 verificado), y el rechazo de rol incorrecto (`@RolesAllowed`) con un
> token de estudiante contra un endpoint admin-only (403 verificado). Todo devolvió
> exactamente los shapes documentados abajo, con los números de agregación
> verificados campo por campo contra los datos sembrados.
>
> Esas pruebas encontraron y corrigieron **varios bugs reales** que ni compilar ni los
> tests unitarios habían detectado: (1) un índice de expresión no-IMMUTABLE en el
> esquema, (2) un desajuste entre la estrategia de generación de IDs de Panache y
> `BIGSERIAL`, (3) una incompatibilidad de prefijo bcrypt (`$2b$` de Python vs. `$2a$`
> que exige WildFly Elytron) que habría bloqueado el login de todo usuario migrado
> por el ETL, y (4) un `ClassCastException` en `AnalyticsService` (Hibernate 7 mapea
> `DATE` nativo a `java.time.LocalDate`, no `java.sql.Date`) que solo se manifestaba
> con filas reales en `consulta_log` — con la tabla vacía el bug era invisible. Más
> tarde, al agregar el módulo de estudio, apareció un quinto: (5) la app **no arrancaba**
> si `GOOGLE_APPLICATION_CREDENTIALS_JSON` estaba vacía (SmallRye Config convierte "" a
> null y rompe la inyección de un `String` plano). Ahora Firebase degrada con un WARN
> legible y la app levanta igual. Todos están corregidos en el código actual.

### Tip para quien pruebe esto con Postman: usar el emulador de Firebase Auth

Si no tenés un proyecto Firebase real a mano todavía, podés probar el flujo de login
completo (incluido el canje de token) 100% local:

```bash
npx firebase-tools emulators:start --only auth --project cualquier-nombre
# En otra terminal, antes de levantar el backend:
export FIREBASE_AUTH_EMULATOR_HOST=localhost:9099
```

Y el canje de token en Postman apunta al emulador en vez de a Google:
```
POST http://localhost:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=fake-api-key
```
(cualquier string sirve como `key` contra el emulador). El resto del flujo es idéntico.

---

## Requisitos para correr esto y probarlo de verdad

El código compila (`mvn compile` → BUILD SUCCESS) pero para levantarlo y hacer requests
reales hace falta aprovisionar:

1. **PostgreSQL** (Supabase o local) — `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`.
   Flyway aplica el esquema solo (`V1__init_schema.sql`) al arrancar.
2. **Al menos un estudiante o docente en la tabla `estudiantes`/`docentes`** — o correr el
   ETL (`etl/`) contra una hoja de Sheets real, o insertar una fila a mano con
   `password_hash = bcrypt(codigo)` (ej. con `htpasswd -bnBC 10 "" E001 | cut -d: -f2`
   o cualquier generador bcrypt).
3. **Proyecto Firebase** — `FIREBASE_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS_JSON`
   (JSON completo de una service account con permiso "Firebase Authentication Admin"), y
   la **Web API Key** del proyecto (Firebase Console → Configuración del proyecto →
   General → "Clave de API web") — esta última la necesita Postman para el paso 2 del
   flujo de login (ver abajo), no el backend.
4. **`GEMINI_API_KEY`** — para que `/chat` funcione de verdad (si falta, `/chat`
   responde `503`).
5. Opcional para el flujo de agendado end-to-end: credenciales de Calendar (las mismas
   de Firebase sirven si la service account tiene acceso al calendario) y
   `TELEGRAM_BOT_TOKEN` + `TELEGRAM_NOTIFICATIONS_CHAT_ID`.

Arrancar en modo dev: `cd backend-quarkus && ./mvnw quarkus:dev` → `http://localhost:8080`.

---

## ⚠️ Importante para Postman: el login NO devuelve un token usable directamente

Esto es lo más importante que la IA que arme la colección debe saber, porque es distinto
a un login JWT clásico:

```
POST /auth/login  →  { "token": "<CUSTOM TOKEN DE FIREBASE>", ... }
```

Ese `token` **no es** un Bearer token válido — es un *custom token* de Firebase. En el
navegador, el SDK de Firebase JS lo canjea automáticamente por un ID token real
(`signInWithCustomToken` + `getIdToken()`). Postman no tiene el SDK de Firebase, así que
hay que replicar ese canje con una llamada REST extra a la API pública de Firebase Auth:

```
POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=<WEB_API_KEY>
Content-Type: application/json

{ "token": "<el custom token de /auth/login>", "returnSecureToken": true }
```

Respuesta:
```json
{ "idToken": "eyJhbGciOi...", "refreshToken": "...", "expiresIn": "3600", ... }
```

**Ese `idToken` (no el `token` de `/auth/login`) es el que va en
`Authorization: Bearer <idToken>` en todas las llamadas protegidas.** Expira en 1 hora.

**Recomendación para la colección de Postman**: un test script en la request de
`/auth/login` que guarde `token` en una variable de colección
(`pm.collectionVariables.set("customToken", pm.response.json().token)`), y un segundo
request "Canjear token (Firebase)" apuntando a la URL de arriba con un test script que
guarde `idToken` en otra variable de colección
(`pm.collectionVariables.set("idToken", pm.response.json().idToken)`). Todas las
requests protegidas usan `Authorization: Bearer {{idToken}}`.

---

## `GET /health`

Sin autenticación. Usado como healthcheck.

**Response 200:**
```json
{ "status": "ok", "service": "utpbot-api", "version": "3.0.0" }
```

## `GET /`

Sin autenticación.

**Response 200:**
```json
{
  "nombre": "UTPBot API",
  "version": "3.0.0",
  "estado": "✅ Activo",
  "descripcion": "API del Asistente Académico Virtual de la UTP (backend Quarkus).",
  "documentacion": "/q/swagger-ui"
}
```

---

## `POST /auth/login`

Sin autenticación previa (es el endpoint que la otorga).

**Request:**
```json
{ "codigo": "E001", "password": "E001" }
```
> Contraseña placeholder: hoy, para estudiantes/docentes, la contraseña ES el código
> institucional (hasheado con bcrypt en la base, pero el valor en texto plano que se
> envía es literalmente el código). Para el admin, la contraseña real está en
> `ADMIN_PASSWORD_HASH`/`ADMIN_USERNAME` — variables de entorno, valor real solo lo
> tiene quien desplegó.

**Response 200 (éxito):**
```json
{
  "token": "eyJhbGciOiJSUzI1NiIs...",
  "nombre": "Ana García López",
  "rol": "estudiante",
  "idioma": "es",
  "codigo": "E001"
}
```
`rol` ∈ `estudiante` | `docente` | `admin`.

**Errores** (todos con shape `{"detail": "..."}`):
| Status | Caso |
|---|---|
| 400 | `codigo`/`password` vacíos (validación Bean Validation) |
| 401 | Contraseña incorrecta |
| 404 | Código institucional no encontrado |
| 503 | Login de admin pero `ADMIN_PASSWORD_HASH` no configurado |

**Casos de prueba sugeridos para la colección:**
1. Login estudiante válido → 200
2. Login docente válido → 200
3. Login admin válido (si `ADMIN_USERNAME`/`ADMIN_PASSWORD_HASH` configurados) → 200
4. Contraseña incorrecta → 401 `{"detail": "Contraseña incorrecta."}`
5. Código inexistente → 404 `{"detail": "Código institucional no encontrado."}`
6. Body vacío / campos faltantes → 400

---

## `POST /chat`

**Requiere** `Authorization: Bearer <idToken>` (ver sección de arriba). Roles permitidos:
`estudiante`, `docente`, `admin` (401 si falta/inválido el token, 403 si el rol del
token no es ninguno de esos tres — en la práctica siempre lo será).

**Request:**
```json
{
  "codigo_usuario": "E001",
  "rol": "estudiante",
  "mensaje": "¿Cuál es mi horario de esta semana?",
  "idioma_preferido": "es",
  "historial": [
    { "rol": "user", "contenido": "Hola" },
    { "rol": "assistant", "contenido": "¡Hola! ¿En qué puedo ayudarte?" }
  ],
  "file_name": null,
  "file_mime": null,
  "file_data": null
}
```

> **`codigo_usuario` y `rol` deben coincidir EXACTAMENTE con el usuario autenticado por
> el token** (el backend los valida contra el token, no confía en el body — es una
> corrección de seguridad respecto al backend Python original). Si no coinciden →
> `403 {"detail": "No autorizado a operar como otro usuario."}`. Para Postman: setear
> estos dos campos con variables de colección pobladas desde la respuesta de
> `/auth/login` (`{{codigoLogueado}}`, `{{rolLogueado}}`), no a mano.

**Campos de archivo adjunto (opcionales):** `file_name` (ej. `"silabo.pdf"`), `file_mime`
(ej. `"application/pdf"`, `"application/vnd.openxmlformats-officedocument.wordprocessingml.document"`
para .docx, `"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"` para
.xlsx, `"text/csv"`, `"text/plain"`), `file_data` (el archivo codificado en **base64 puro**,
sin el prefijo `data:...;base64,`).

**Response 200:**
```json
{
  "respuesta": "Tu horario de esta semana es:\n\n- Lunes 08:00-10:00: Algoritmos...",
  "sugerencias": [
    "¿Cuáles son mis notas?",
    "¿Cuándo es mi próximo examen?",
    "¿Qué significa el código de mi aula?"
  ]
}
```
`sugerencias` siempre trae exactamente 3 elementos.

**Errores:**
| Status | Caso |
|---|---|
| 400 | `mensaje`/`codigo_usuario`/`rol` vacíos |
| 401 | Sin token / token inválido o expirado |
| 403 | `codigo_usuario`/`rol` del body no coincide con el token |
| 503 | `GEMINI_API_KEY` no configurada |

**Casos de prueba sugeridos:**
1. Mensaje simple sin historial (primer mensaje) → 200, respuesta con saludo inicial
2. Mensaje con historial (no debería saludar de nuevo)
3. Pregunta con `idioma_preferido: "en"` → respuesta completa en inglés
4. Adjuntar un PDF pequeño (`file_mime: "application/pdf"`) → el asistente lo analiza
5. Pedir agendar una sesión de estudio ("agéndame una hora de estudio mañana de 4 a 6pm
   para el examen de cálculo") → dispara `agendar_tiempo_estudio` (requiere Calendar +
   Telegram configurados para funcionar de punta a punta; si no, revisar el mensaje de
   error que igual debe devolver 200 con texto explicando el fallo)
6. `codigo_usuario` que NO coincide con el usuario logueado → 403
7. Sin header `Authorization` → 401
8. Mensaje vacío → 400

---

## `GET /admin/*` — requiere rol `admin`

Todos devuelven `401` sin token y `403` con un token de rol `estudiante`/`docente`
(verificado en vivo con datos reales — ver nota arriba).

| Endpoint | Response |
|---|---|
| `GET /admin/dashboard` | `{overview, por_dia, por_categoria, por_rol, recientes}` — el dashboard completo, un solo request |
| `GET /admin/stats/overview` | Solo el bloque `overview` de arriba |
| `GET /admin/stats/by-day?dias=30` | `[{fecha:"YYYY-MM-DD", cantidad}]`, relleno de ceros para los `dias` más recientes |
| `GET /admin/stats/by-category` | `[{categoria, cantidad, porcentaje}]`, orden descendente |
| `GET /admin/stats/by-role` | `[{rol, cantidad}]`, orden descendente |
| `GET /admin/recent-logs?limite=20` | `[{fecha, codigo_usuario, rol, categoria, pregunta}]`, más reciente primero |
| `GET /admin/faq-analytics` | `{total_consultas, categorias:[{categoria, cantidad, preguntas_ejemplo:[...hasta 5]}]}` |

**Ejemplo real** (`/admin/dashboard`, con 4 filas de prueba sembradas):
```json
{
  "overview": {
    "total_consultas": 4, "consultas_hoy": 1, "usuarios_activos": 2,
    "categoria_top": "notas", "porcentaje_estudiantes": 75.0, "porcentaje_docentes": 25.0
  },
  "por_categoria": [
    {"categoria": "notas", "cantidad": 2, "porcentaje": 50.0},
    {"categoria": "horarios", "cantidad": 1, "porcentaje": 25.0},
    {"categoria": "docente", "cantidad": 1, "porcentaje": 25.0}
  ],
  "por_rol": [{"rol": "estudiante", "cantidad": 3}, {"rol": "docente", "cantidad": 1}],
  "por_dia": [{"fecha": "2026-08-14", "cantidad": 1}, "...29 días más..."],
  "recientes": [{"fecha": "2026-08-14 20:50:06", "codigo_usuario": "E001", "rol": "estudiante", "categoria": "horarios", "pregunta": "¿Cuál es mi horario?"}]
}
```

**Nota:** el dashboard arranca completamente en cero/vacío hasta que haya tráfico real
de `/chat` — el histórico de `FAQ_Log` de Sheets NO se migra (decisión del usuario).

**No se implementó `POST /admin/update-sheet`** (edición genérica de celda) — decisión
explícita del plan de migración: en Postgres sería un riesgo de inyección/autorización
si se genericiza, y el frontend actual no lo usa. Reemplazarlo por CRUD tipado por
entidad queda como trabajo futuro no bloqueante.

---

## `GET /docente/{seccion,resumen}/{codigo}` — requiere rol `docente`

Solo el propio docente puede consultar su código — `403` si el `codigo` de la URL no
coincide con el del token (verificado en vivo).

**`GET /docente/resumen/{codigo}`** — ejemplo real:
```json
{
  "codigo_docente": "D001", "nombre": "Dr. Roberto Flores", "departamento": "Ingeniería",
  "total_secciones": 1, "total_alumnos": 1, "cursos": ["Algoritmos"]
}
```

**`GET /docente/seccion/{codigo}`** — `{codigo_docente, total_secciones, secciones:[{curso, seccion, horario, estudiantes:[{codigo, nombre, notas, asistencia}]}]}`, o `{codigo_docente, secciones:[], message}` si no tiene secciones asignadas.

**Casos de prueba:**
1. Docente consultando su propio código → 200
2. Docente consultando el código de OTRO docente → 403 `{"detail": "Solo puedes consultar tu propia información como docente."}`
3. Código de docente inexistente (pero es el propio, no debería pasar en la práctica) → 404
4. Sin token → 401

---

## `POST /telegram/webhook` — público, sin autenticación

Lo llama Telegram, no un usuario logueado. Responde `{"ok": true}` siempre (salvo body
totalmente ausente), incluso si el update no trae un mensaje procesable — así Telegram
nunca reintenta de más. El procesamiento real (enviar la respuesta canned) corre en un
hilo aparte, sin bloquear la respuesta del webhook (Telegram exige responder en &lt;5s).

**Request** (payload real de Telegram, solo importan `message.chat.id` y `message.text`):
```json
{
  "update_id": 123456,
  "message": {
    "message_id": 1,
    "from": {"id": 999888777, "first_name": "Juan", "is_bot": false},
    "chat": {"id": 999888777, "type": "private"},
    "date": 1700000000,
    "text": "/start"
  }
}
```
**Response 200:** `{"ok": true}` — siempre, sea cual sea el resultado del procesamiento.

**Casos de prueba:**
1. `/start` o `/inicio` → el bot responde (canned) con el mensaje de bienvenida
2. `/ayuda` o `/help` → mensaje de ayuda
3. Cualquier otro texto → mensaje recordatorio ("este bot es solo de notificaciones")
4. `{"callback_query": {...}}` (sin `message`) → `{"ok": true}`, se ignora
5. Body vacío `{}` → `{"ok": true}`, se ignora

## `GET|POST /telegram/setup-webhook` — público, sin autenticación (igual que el original)

Registra el webhook ante Telegram usando `TELEGRAM_WEBHOOK_URL`.

**Response 200 (éxito):** `{"success": true, "message": "Webhook configurado correctamente en: ...", "result": {...}}`
**Errores:** `503` si falta `TELEGRAM_BOT_TOKEN` o `TELEGRAM_WEBHOOK_URL`; `500` si Telegram rechaza la URL.

## `GET /telegram/status` — público, sin autenticación

**Response 200** (ejemplo con nada configurado, verificado en vivo):
```json
{
  "token_configurado": false, "webhook_configurado": false, "webhook_url": null,
  "chat_id_configurado": false, "chat_id_notificaciones": null, "listo": false
}
```

---

## `POST /transcribe`

**Requiere** `Authorization: Bearer <idToken>`, mismos roles que `/chat`.

**Request:**
```json
{ "audio_base64": "<audio codificado en base64>", "mime_type": "audio/webm" }
```

**Response 200:**
```json
{ "texto": "cuál es mi horario de clases", "confianza": 1.0 }
```
`texto` viene vacío `""` si Gemini detecta silencio/ruido — no es un error, es una
respuesta 200 válida.

**Errores:** `400` si el audio decodificado pesa menos de 1000 bytes o el base64 es
inválido; `503` si falta `GEMINI_API_KEY`; `401` sin token.

---

## `/estudio/*` — Módulo de estudio personalizado

Todos requieren `Authorization: Bearer <idToken>` y **rol `estudiante`**. Cada alumno
solo accede a lo suyo: el código sale del token verificado, nunca de la URL o el body
(verificado en vivo — un alumno recibe `404` al pedir material de otro).

### Historia 1 — Sílabo → ruta de estudio

```
POST /estudio/materiales              subir el sílabo (tipo: "SILABO")
POST /estudio/materiales/{id}/ruta    generar la ruta con IA
GET  /estudio/rutas                   listar rutas
GET  /estudio/rutas/{id}              ruta con sus temas
PATCH /estudio/temas/{id}             marcar tema completado
```

**Subir material** (el archivo va en base64, igual que en `/chat`):
```json
{
  "nombre_archivo": "silabo_seguridad.pdf",
  "mime_type": "application/pdf",
  "file_data": "<base64 puro, sin el prefijo data:...>",
  "tipo": "SILABO",
  "codigo_curso": "16947"
}
```
Formatos soportados: **PDF** (PDFBox), **PPTX** (POI), **DOCX**, **XLSX**, TXT/CSV/MD.
Un PDF escaneado (solo imágenes) devuelve `400` porque no tiene texto extraíble.

**Ejemplo real** de ruta generada desde un sílabo de Seguridad Informática:
```json
{
  "id": 2, "curso": "SEGURIDAD INFORMATICA (16947)",
  "titulo": "Ruta de Estudio: Seguridad Informática Esencial",
  "total_temas": 6, "temas_completados": 0,
  "temas": [
    {"id": 7, "orden": 1, "titulo": "Fundamentos de la Seguridad Informática",
     "descripcion": "...", "horas_estimadas": 5.0, "completado": false}
  ]
}
```

### Historia 2 — Materiales → cuestionarios y resúmenes

```
POST /estudio/materiales/{id}/cuestionario?preguntas=8    generar cuestionario
GET  /estudio/cuestionarios                                listar
GET  /estudio/cuestionarios/{id}                           con preguntas y respuestas
POST /estudio/materiales/{id}/resumen                      generar resumen
```

Los cuestionarios son de **autoevaluación**: la respuesta correcta y la explicación
vienen en la respuesta (el alumno estudia con ellos, no es un examen calificado).
`preguntas` se acota automáticamente entre 3 y 20.

```json
{
  "titulo": "Cuestionario de Autoevaluación: Inteligencia de Negocios (31662)",
  "preguntas": [{
    "orden": 1,
    "enunciado": "¿Cuál es el objetivo principal del curso...?",
    "opciones": ["Desarrollar software...", "Transformar datos...", "...", "..."],
    "indice_correcto": 1,
    "explicacion": "La sumilla del curso establece que..."
  }]
}
```

### Historia 3 — Meta diaria y racha

```
GET  /estudio/meta        meta + estado de la racha
PUT  /estudio/meta        fijar meta   {"minutos_diarios": 45}
POST /estudio/sesiones    registrar estudio {"minutos": 30, "tema_id": 7, "nota": "..."}
GET  /estudio/racha       igual que GET /estudio/meta
```

```json
{
  "minutos_diarios": 45, "minutos_hoy": 55, "meta_cumplida_hoy": true,
  "racha_actual": 5, "mejor_racha": 5, "minutos_semana": 155,
  "ultimos7_dias": [{"fecha": "2026-08-17", "minutos": 55, "cumplida": true}]
}
```

**Reglas de la racha** (verificadas con casos de borde en vivo):
- Se cuenta en días del **calendario de Lima**, no UTC: estudiar 23:30 hora peruana
  cuenta para ese día.
- Si **hoy** todavía no cumpliste, la racha **no se corta** — se cuenta desde ayer. De
  lo contrario todo alumno vería racha 0 cada mañana.
- Un día sin cumplir en el medio sí corta la racha.

Validaciones: sesión entre 1 y 1440 minutos, meta entre 5 y 720 (devuelven `422`).

**Casos de prueba sugeridos:**
1. Subir un sílabo PDF → generar ruta → verificar temas ordenados
2. Subir un PPTX → generar cuestionario de 5 preguntas
3. Fijar meta 45 → registrar 25 min (no cumplida) → registrar 30 más (cumplida, racha 1)
4. Intentar ver `/estudio/materiales/{id}` de otro alumno → `404`
5. Registrar sesión de 0 minutos → `422`

---

## Variables de entorno para levantar el backend en dev

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/utpbot_dev
DATABASE_USER=utpbot
DATABASE_PASSWORD=utpbot

FIREBASE_PROJECT_ID=tu-proyecto-firebase
GOOGLE_APPLICATION_CREDENTIALS_JSON='{"type":"service_account",...}'

GEMINI_API_KEY=tu_api_key_de_gemini
GEMINI_MODEL=gemini-2.5-pro

ADMIN_USERNAME=admin
ADMIN_PASSWORD_HASH=$2a$10$...   # bcrypt, no la contraseña en texto plano

TELEGRAM_BOT_TOKEN=...
TELEGRAM_NOTIFICATIONS_CHAT_ID=...
GOOGLE_CALENDAR_ID=primary
```

## Headers comunes a toda la colección

```
Content-Type: application/json
Authorization: Bearer {{idToken}}   ← solo en endpoints protegidos
```

Base URL sugerida como variable de colección: `{{baseUrl}}` = `http://localhost:8080`
en dev.
