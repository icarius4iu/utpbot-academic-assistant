/**
 * UTPBot Sync — background.js (service worker MV3)
 *
 * Maneja la sesión de UTPBot y el POST al backend.
 *
 * IMPORTANTE — diferencia con la guía original: UTPBot NO usa login de Firebase con
 * email/contraseña. El flujo real (implementado en AuthResource/AuthService del
 * backend) es:
 *
 *   1. POST {backend}/auth/login  {codigo, password}
 *        → el backend valida contra Postgres (bcrypt) y devuelve un CUSTOM TOKEN
 *          de Firebase en el campo "token"
 *   2. POST identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken
 *        → canjea ese custom token por un ID token real + refresh token
 *   3. El ID token es el que va como Bearer en todas las llamadas protegidas
 *   4. Cuando vence (~1h), se renueva con securetoken.googleapis.com
 *
 * Así el alumno usa las MISMAS credenciales que en la web de UTPBot (su código
 * institucional y su contraseña), sin una cuenta separada.
 */

const DEFAULTS = {
  backendUrl: "http://localhost:8000",
  // Web API Key pública del proyecto Firebase de UTPBot (no es un secreto: va
  // embebida en el frontend). Editable desde el popup.
  firebaseApiKey: "AIzaSyDGjOFIgp0_Zi5A-AbQR64fjKZWrWQW7KM"
};

async function config() {
  const guardado = await chrome.storage.local.get(["backendUrl", "firebaseApiKey"]);
  return {
    backendUrl: guardado.backendUrl || DEFAULTS.backendUrl,
    firebaseApiKey: guardado.firebaseApiKey || DEFAULTS.firebaseApiKey
  };
}

/** Paso 1+2: login contra UTPBot y canje del custom token por un ID token de Firebase. */
async function login(codigo, password) {
  const { backendUrl, firebaseApiKey } = await config();
  if (!firebaseApiKey) throw new Error("Falta la Firebase Web API Key (ver Configuración).");

  const rLogin = await fetch(backendUrl.replace(/\/$/, "") + "/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ codigo, password })
  });
  const datosLogin = await rLogin.json().catch(() => ({}));
  if (!rLogin.ok) {
    throw new Error(datosLogin.detail || "Login falló (HTTP " + rLogin.status + ")");
  }
  if (datosLogin.rol !== "estudiante") {
    throw new Error("Esta extensión es solo para estudiantes (tu rol: " + datosLogin.rol + ").");
  }

  const rCanje = await fetch(
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=" + firebaseApiKey,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: datosLogin.token, returnSecureToken: true })
    }
  );
  const canje = await rCanje.json().catch(() => ({}));
  if (!rCanje.ok) {
    throw new Error("No pude validar la sesión con Firebase: " + ((canje.error && canje.error.message) || rCanje.status));
  }

  await chrome.storage.local.set({
    idToken: canje.idToken,
    refreshToken: canje.refreshToken,
    idTokenExp: Date.now() + Number(canje.expiresIn) * 1000,
    codigo: datosLogin.codigo,
    nombre: datosLogin.nombre
  });

  return { codigo: datosLogin.codigo, nombre: datosLogin.nombre };
}

/** Paso 4: devuelve un ID token vigente, renovándolo si está por vencer. */
async function idTokenVigente() {
  const { firebaseApiKey } = await config();
  const s = await chrome.storage.local.get(["idToken", "refreshToken", "idTokenExp"]);
  if (!s.refreshToken) throw new Error("No hay sesión de UTPBot. Iniciá sesión en el popup.");

  // 60s de margen para que no venza en pleno request
  if (s.idToken && s.idTokenExp && Date.now() < s.idTokenExp - 60000) return s.idToken;

  const r = await fetch("https://securetoken.googleapis.com/v1/token?key=" + firebaseApiKey, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "refresh_token", refresh_token: s.refreshToken })
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error("No pude renovar la sesión: " + ((j.error && j.error.message) || r.status));

  await chrome.storage.local.set({
    idToken: j.id_token,
    refreshToken: j.refresh_token,
    idTokenExp: Date.now() + Number(j.expires_in) * 1000
  });
  return j.id_token;
}

async function sincronizar(payload) {
  const { backendUrl } = await config();
  const idToken = await idTokenVigente();

  const r = await fetch(backendUrl.replace(/\/$/, "") + "/estudiante/sincronizar", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": "Bearer " + idToken },
    body: JSON.stringify(payload)
  });

  const texto = await r.text();
  let cuerpo;
  try { cuerpo = JSON.parse(texto); } catch (e) { cuerpo = texto; }
  return { ok: r.ok, status: r.status, body: cuerpo };
}

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (!msg || !msg.type) return;

  if (msg.type === "UTPBOT_LOGIN") {
    login(msg.codigo, msg.password)
      .then((r) => sendResponse({ ok: true, ...r }))
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e) }));
    return true;
  }

  if (msg.type === "UTPBOT_POST") {
    sincronizar(msg.payload)
      .then((r) => sendResponse(r))
      .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e) }));
    return true;
  }
});
