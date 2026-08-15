# UTPBot Sync — Extensión de navegador

Lleva tus cursos y horario del **Portal del Estudiante UTP** (`class.utp.edu.pe`) a
**UTPBot**, con un clic y sin copiar nada a mano.

Basada en `etl/UTPBotSync_GuiaImplementacion.md` (investigación de la API real del
portal) y `etl/SCRAPER_PLUGIN_SPEC.md` (principios de diseño).

---

## Qué hace (y qué NO hace)

✅ Lee la API interna del portal (`api-pao.utpxpedition.com`) **con tu propia sesión ya
iniciada**, igual que lo hace la web del portal.
✅ Te muestra un **preview** de todo lo que va a enviar, y solo envía si confirmás.
✅ Escribe **solo en tu propio registro** de UTPBot — el backend toma tu código del
token verificado, no del JSON, así que aunque manipules el payload no podés escribir
datos de otro alumno.

❌ **Nunca** ve ni transmite tu contraseña del Portal del Estudiante. Solo observa los
headers que la propia app del portal ya está mandando.
❌ No corre en segundo plano ni hace polling: solo actúa cuando apretás el botón.
❌ No funciona en ningún sitio que no sea `class.utp.edu.pe`.

---

## Instalación (modo desarrollador)

1. Abrí Chrome → `chrome://extensions`
2. Activá **Modo de desarrollador** (interruptor arriba a la derecha)
3. **Cargar descomprimida** → elegí esta carpeta (`utpbot-sync/`)
4. Copiá el **ID de la extensión** que aparece (lo vas a necesitar si querés restringir
   el CORS del backend en producción)

> Firefox: `about:debugging` → *Este Firefox* → *Cargar complemento temporal* →
> seleccioná `manifest.json`.

## Configuración

Click en el ícono de la extensión → desplegá **Configuración**:

| Campo | Valor |
|---|---|
| URL del backend | `http://localhost:8000` en desarrollo, o la URL de tu Cloud Function en producción |
| Firebase Web API Key | Ya viene precargada con la del proyecto `utpbot-staging` |

## Uso

1. **Iniciá sesión en el Portal del Estudiante** (`class.utp.edu.pe`) y entrá al menos
   una vez a **Cursos** o **Calendario** — es lo que permite a la extensión capturar el
   token de tu sesión.
2. Click en el ícono de la extensión → iniciá sesión con **tus credenciales de UTPBot**
   (el mismo código y contraseña que usás en la web).
3. **Leer datos del portal** → revisá el preview (cuántos cursos, cuántos bloques, qué ciclo).
4. **Confirmar y enviar** → listo.

Podés re-sincronizar cuando quieras: el backend hace *upsert*, así que actualiza en vez
de duplicar (verificado).

---

## Archivos

| Archivo | Rol |
|---|---|
| `manifest.json` | Permisos y declaración de la extensión (Manifest V3) |
| `inject.js` | Corre en el contexto de la página: captura el token de forma pasiva y re-consulta la API del portal (tiene que ser desde ahí por CORS) |
| `content.js` | Puente entre la página y la extensión |
| `background.js` | Service worker: login contra UTPBot, renovación de token y POST al backend |
| `popup.html` / `popup.js` | Interfaz: login, preview y confirmación |

## Cómo funciona el login (importante)

UTPBot **no** usa login de Firebase con email/contraseña. El flujo real es:

```
1. POST {backend}/auth/login  {codigo, password}
      → el backend valida contra Postgres (bcrypt) y devuelve un CUSTOM TOKEN de Firebase
2. POST identitytoolkit.googleapis.com/.../accounts:signInWithCustomToken
      → canjea ese custom token por un ID token + refresh token
3. El ID token va como Bearer en POST /estudiante/sincronizar
4. Al vencer (~1h) se renueva solo con securetoken.googleapis.com
```

Por eso iniciás sesión con tu **código institucional**, no con un email.

---

## Pendientes conocidos

- **Carrera**: el portal no la expone en los 3 endpoints usados (ver §9 de la guía). El
  campo se envía como `null` y el backend **no pisa** el valor que ya tengas cargado.
- **Créditos**: tampoco están en esos endpoints.
- **Aula**: el portal tiene el campo (`classroom`) pero hoy viene vacío. La extensión ya
  lo envía, así que se poblará solo cuando el portal lo complete.
- **Notas**: todavía no se sincronizan (habría que descubrir el endpoint de
  calificaciones con la técnica del §10.1 de la guía).
