# Prompt / Spec — Extensión de navegador para autoexportar datos del Portal del Estudiante a UTPBot

Este documento es el prompt completo para que una IA (o vos mismo) construya una
extensión de navegador (Chrome/Firefox, Manifest V3) que lee los datos académicos
**del propio estudiante logueado** en el Portal del Estudiante de UTP y los sincroniza
con el backend de UTPBot (`backend-quarkus`). Es una vía alternativa/complementaria al
ETL desde Google Sheets (`etl/transform_and_load.py`) — en vez de que un admin cargue
todo de una vez, cada estudiante trae sus propios datos reales con un clic.

---

## 🎯 Objetivo

Un content script que corre SOLO cuando el usuario ya está logueado en el Portal del
Estudiante (con su propia sesión, sin manejar usuario/contraseña de terceros), lee las
páginas de cursos y horario, arma un JSON con el shape exacto que espera el backend, y
lo manda autenticado con el MISMO login que el estudiante ya tiene en UTPBot (Firebase
ID token) — así solo puede escribir sus propios datos, nunca los de otro.

## ⚠️ Principios no negociables (para quien construya esto)

1. **Nunca capturar ni transmitir la contraseña del Portal del Estudiante.** El plugin
   lee páginas que el usuario YA está viendo, autenticado con su propia sesión —
   equivalente a un "copiar y pegar" automatizado, no a un bot que hace login por su
   cuenta.
2. **Nunca leer datos de otro estudiante.** El plugin solo procesa lo que aparece en
   la sesión del usuario actual.
3. **Transparencia**: el plugin debe mostrar claramente qué va a enviar antes de
   mandarlo (un preview/confirmación), no operar en silencio.
4. **Respetar el Portal**: sin scraping agresivo/repetido, sin intentar automatizar el
   login, sin generar tráfico que se parezca a un ataque. Es una lectura puntual,
   disparada por el usuario (botón "Sincronizar"), no un proceso en segundo plano que
   corre solo todo el tiempo.
5. **Antes de tocar el DOM**: revisar la pestaña **Network** del portal con el usuario
   ya logueado — muchos portales modernos cargan los datos vía llamadas
   `fetch`/`XHR` a una API JSON interna. Si existe, es MUCHO más confiable
   engancharse a esa respuesta JSON que parsear el HTML renderizado (que cambia con
   cualquier rediseño visual). Recién si no hay API interna visible, se cae a leer el
   DOM directamente.

---

## 📋 Datos a extraer (mapeados exactamente a nuestro schema)

Basado en las capturas que compartiste (vista de cursos matriculados + vista de
calendario semanal), esto es lo que hay que extraer y a qué campo de
`V1__init_schema.sql` corresponde:

### 1. Perfil del estudiante → tabla `estudiantes`
| Dato visible en el portal | Campo destino | Notas |
|---|---|---|
| Código institucional (si aparece en el perfil/header del portal) | `codigo` | Puede no estar visible en estas capturas — revisar la página de perfil/cuenta |
| Nombre completo | `nombre` | |
| Carrera | `carrera` | Puede no estar en estas vistas — revisar perfil |
| Ciclo actual ("2026 - Ciclo 2 Agosto PREG (001) (Actual)") | `ciclo` | Extraer el número/identificador de ciclo de ese título |

### 2. Cursos matriculados (tarjetas) → tabla `cursos`
Por cada tarjeta de curso visible:
| Dato visible | Campo destino | Ejemplo de la captura |
|---|---|---|
| Nombre del curso | `nombre_curso` | "Ingeniería Económica" |
| Código del curso | — (no está en nuestro schema actual; guardarlo igual como referencia interna del plugin, útil para cruzar con el calendario) | "31088" |
| Modalidad (Presencial / Virtual 24/7) | — (tampoco existe hoy en `cursos`; ver "Campos nuevos a considerar" abajo) | "Presencial" |
| Docente asignado | usado para `horarios.docente_nombre`, no para `cursos` directamente | "Betty Karol Zamora Yansi" |
| Estado | `estado` | Inferir "en curso" si el curso aparece activo en el ciclo actual |
| Créditos | `creditos` | **No visible en estas capturas** — si no está en ninguna vista, dejar en null/0 y decidir después si hace falta |

### 3. Horario semanal (vista calendario) → tabla `horarios`
Por cada bloque de clase en el calendario:
| Dato visible | Campo destino | Ejemplo |
|---|---|---|
| Curso (nombre + código, ej. "Inteligencia de Negocios (31662)") | `curso` (usar el nombre, cruzando por código con la tarjeta de arriba) | |
| Día de la semana (columna del calendario) | `dia` | "Viernes" |
| Hora de inicio / fin | `hora_inicio`, `hora_fin` | "06:30 p.m. – 08:00 p.m." → convertir a formato 24h `18:30`/`20:00` |
| Modalidad (tag "Presencial"/"Virtual 24/7") | no existe campo hoy — ver abajo | |
| Aula | `aula` | **No visible en estas capturas** — revisar si hay una vista de detalle del bloque (clic en el evento del calendario) que muestre el aula física |
| Docente | `docente_nombre` | Cruzar con el nombre visible en la tarjeta del curso correspondiente |

### 4. Notas (si el portal las expone en alguna sección) → tabla `notas`
No aparece en las capturas compartidas — si el portal tiene una sección de
calificaciones, mapear:
| Dato | Campo destino |
|---|---|
| Curso | `curso` |
| Nota parcial | `parcial` |
| Nota final | `final` |
| Promedio | `promedio` |

### Campos nuevos a considerar (no existen en el schema actual)
El portal real expone información que nuestro modelo actual no captura:
- **Modalidad del curso** (Presencial / Virtual 24/7) — hoy no hay columna para esto
  en `cursos` ni `horarios`.
- **Progreso del curso** (los porcentajes "1%", "4%", "5%" en las tarjetas).

Si estos datos son valiosos para el asistente (ej. "¿mi curso es virtual o
presencial?"), avisame y agrego las columnas correspondientes al esquema antes de que
el plugin las mande — mejor decidirlo ahora que descartar datos que el scraper ya
extrajo.

---

## 📤 Contrato JSON que el plugin debe enviar

Diseño propuesto (endpoint nuevo, **todavía no implementado en el backend** — lo hago
en cuanto confirmes el shape final):

```
POST /estudiante/sincronizar
Authorization: Bearer <ID token de Firebase del propio estudiante>
Content-Type: application/json
```

```json
{
  "perfil": {
    "carrera": "Ingeniería de Sistemas",
    "ciclo": "2026-2"
  },
  "cursos": [
    {
      "codigo_curso": "31088",
      "nombre_curso": "Ingeniería Económica",
      "modalidad": "Presencial",
      "docente": "Betty Karol Zamora Yansi",
      "progreso_porcentaje": 5
    }
  ],
  "horarios": [
    {
      "codigo_curso": "31662",
      "dia": "Viernes",
      "hora_inicio": "18:30",
      "hora_fin": "20:00",
      "modalidad": "Presencial",
      "aula": null
    }
  ]
}
```

**Reglas del lado del backend** (a implementar):
- El endpoint exige rol `estudiante` (`@RolesAllowed("estudiante")`).
- Solo puede escribir en el registro del **propio** `codigo` — se toma del token
  verificado (`CurrentUser`), igual que ya hacemos en `ChatResource`/`DocenteResource`,
  nunca de un campo que mande el cliente.
- Comportamiento **upsert**: si el curso/horario ya existe (mismo `codigo_curso` para
  ese estudiante), se actualiza; si no, se crea. Así el estudiante puede
  "re-sincronizar" cuando cambie de ciclo sin duplicar filas.

---

## 🔧 Arquitectura sugerida del plugin

```
manifest.json          (Manifest V3, permission: solo el dominio del Portal del Estudiante)
content-script.js      (corre en las páginas del portal, extrae los datos)
popup.html/popup.js    (botón "Sincronizar con UTPBot" + preview de qué se va a enviar)
background.js          (opcional: maneja el token de Firebase y el POST al backend)
```

**Flujo:**
1. Usuario logueado en el Portal del Estudiante Y en UTPBot (mismo navegador).
2. Click en el ícono del plugin → botón "Sincronizar".
3. El content script lee la(s) página(s) actuales (cursos + calendario).
4. Popup muestra un preview legible ("Vamos a enviar: 6 cursos, 6 bloques de
   horario...") — el usuario confirma.
5. El plugin toma el ID token de Firebase (si UTPBot está abierto en otra pestaña,
   usar `chrome.tabs`/`postMessage` para pedírselo a esa pestaña; si no, pedirle al
   usuario que inicie sesión en UTPBot primero).
6. `POST /estudiante/sincronizar` con el JSON armado.
7. Muestra confirmación de éxito/error.

---

## ✅ Antes de escribir código: lo que falta decidir con datos reales

No tengo acceso al HTML real del portal (solo capturas de pantalla), así que quien
construya esto necesita, con DevTools abierto en el portal real:

1. **Revisar la pestaña Network** al cargar la página de cursos y la de calendario —
   ¿hay una llamada a una API JSON? Si sí, copiar la URL y el shape exacto de la
   respuesta (esto simplifica todo enormemente).
2. Si no hay API visible, inspeccionar el DOM real (botón derecho → Inspeccionar
   sobre una tarjeta de curso) para conseguir selectores CSS estables (evitar clases
   generadas automáticamente tipo `css-x7h2k9`, preferir atributos `data-*` o
   estructura semántica).
3. Confirmar dónde está el **código institucional** y la **carrera** del estudiante
   (probablemente en una página de perfil separada, no en las dos capturas que
   compartiste).
4. Confirmar si existe una vista de **aula física** por bloque de horario (clic en un
   evento del calendario suele abrir un detalle).
5. Confirmar si hay una sección de **notas/calificaciones** en el portal.

---

## Siguiente paso

Con esta spec ya podés pegarla en otra conversación/herramienta para que te ayude a
escribir el `content-script.js` real (necesita los selectores/API reales del paso
anterior, que solo se consiguen con el portal abierto). Cuando tengas el shape de
datos confirmado, avisame y:
1. Ajusto el schema de Postgres si hace falta (modalidad, progreso, aula por bloque).
2. Implemento `POST /estudiante/sincronizar` en el backend.
3. Probamos el flujo completo con tu cuenta real.
