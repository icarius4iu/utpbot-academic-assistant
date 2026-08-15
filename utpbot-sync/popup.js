/**
 * UTPBot Sync — popup.js
 *
 * Flujo: login (código+contraseña de UTPBot) → leer portal → preview → confirmar envío.
 * El login real lo hace background.js (ver ahí la explicación del custom token).
 */

const $ = (id) => document.getElementById(id);
let payloadActual = null;

// ─────────── Configuración ───────────
async function cargarConfig() {
  const c = await chrome.storage.local.get(["backendUrl", "firebaseApiKey"]);
  $("backendUrl").value = c.backendUrl || "http://localhost:8000";
  $("firebaseApiKey").value = c.firebaseApiKey || "AIzaSyDGjOFIgp0_Zi5A-AbQR64fjKZWrWQW7KM";
}

$("btnGuardarCfg").onclick = async () => {
  await chrome.storage.local.set({
    backendUrl: $("backendUrl").value.trim(),
    firebaseApiKey: $("firebaseApiKey").value.trim()
  });
  $("msg").innerHTML = '<span class="ok">Configuración guardada.</span>';
};

// ─────────── Estado de sesión ───────────
async function refrescarUI() {
  const s = await chrome.storage.local.get(["refreshToken", "codigo", "nombre"]);
  if (s.refreshToken) {
    $("login").classList.add("hide");
    $("sesion").classList.remove("hide");
    $("quien").textContent = s.nombre || s.codigo || "UTPBot";
  } else {
    $("login").classList.remove("hide");
    $("sesion").classList.add("hide");
  }
}

// ─────────── Login ───────────
$("btnLogin").onclick = () => {
  $("loginMsg").textContent = "";
  const codigo = $("codigo").value.trim();
  const password = $("password").value;
  if (!codigo || !password) {
    $("loginMsg").textContent = "Completá código y contraseña.";
    return;
  }

  $("btnLogin").disabled = true;
  $("btnLogin").textContent = "Ingresando…";

  chrome.runtime.sendMessage({ type: "UTPBOT_LOGIN", codigo, password }, async (res) => {
    $("btnLogin").disabled = false;
    $("btnLogin").textContent = "Iniciar sesión";
    if (!res) {
      $("loginMsg").textContent = "Sin respuesta de la extensión. Recargala e intentá de nuevo.";
      return;
    }
    if (!res.ok) {
      $("loginMsg").textContent = res.error;
      return;
    }
    $("password").value = "";
    await refrescarUI();
  });
};

$("logout").onclick = async (e) => {
  e.preventDefault();
  await chrome.storage.local.remove(["idToken", "refreshToken", "idTokenExp", "codigo", "nombre"]);
  payloadActual = null;
  $("preview").classList.add("hide");
  $("msg").textContent = "";
  await refrescarUI();
};

// ─────────── Leer el portal (preview antes de enviar) ───────────
$("btnLeer").onclick = async () => {
  $("msg").textContent = "Leyendo el portal…";
  $("preview").classList.add("hide");

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab || !/class\.utp\.edu\.pe/.test(tab.url || "")) {
    $("msg").innerHTML = '<span class="err">Abrí primero el Portal del Estudiante (class.utp.edu.pe) en esta pestaña.</span>';
    return;
  }

  chrome.tabs.sendMessage(tab.id, { type: "UTPBOT_BUILD" }, (res) => {
    if (chrome.runtime.lastError || !res) {
      $("msg").innerHTML = '<span class="err">No pude leer la página. Recargá el portal (F5) y reintentá.</span>';
      return;
    }
    if (!res.ok) {
      $("msg").innerHTML = '<span class="err">' + res.error + "</span>";
      return;
    }

    payloadActual = res.payload;
    const nCursos = (res.payload.cursos || []).length;
    const nHorarios = (res.payload.horarios || []).length;
    const ciclo = res.payload.perfil.ciclo || "ciclo desconocido";

    $("resumen").innerHTML =
      "Vas a enviar <b>" + nCursos + " cursos</b> y <b>" + nHorarios + " bloques de horario</b><br>" +
      '<span class="muted">Ciclo: ' + ciclo + "</span>";
    $("json").textContent = JSON.stringify(res.payload, null, 2);
    $("preview").classList.remove("hide");
    $("msg").textContent = "";
  });
};

$("btnCancelar").onclick = () => {
  $("preview").classList.add("hide");
  payloadActual = null;
  $("msg").textContent = "";
};

// ─────────── Enviar al backend ───────────
$("btnEnviar").onclick = () => {
  if (!payloadActual) return;
  $("msg").textContent = "Enviando…";
  $("btnEnviar").disabled = true;

  chrome.runtime.sendMessage({ type: "UTPBOT_POST", payload: payloadActual }, (res) => {
    $("btnEnviar").disabled = false;

    if (!res) {
      $("msg").innerHTML = '<span class="err">Sin respuesta del backend.</span>';
      return;
    }
    if (!res.ok) {
      const detalle = res.error || (res.body && res.body.detail) || JSON.stringify(res.body || "");
      $("msg").innerHTML = '<span class="err">Error ' + (res.status || "") + ": " + detalle + "</span>";
      return;
    }

    const r = res.body || {};
    let html = '<span class="ok">✓ Sincronizado.</span><br><span class="muted">' +
      "Cursos: " + (r.cursos_creados || 0) + " nuevos, " + (r.cursos_actualizados || 0) + " actualizados · " +
      "Horarios: " + (r.horarios_creados || 0) + " nuevos, " + (r.horarios_actualizados || 0) + " actualizados</span>";

    if (r.avisos && r.avisos.length) {
      html += '<br><span class="aviso">' + r.avisos.join("<br>") + "</span>";
    }
    $("msg").innerHTML = html;
    $("preview").classList.add("hide");
    payloadActual = null;
  });
};

cargarConfig().then(refrescarUI);
