/**
 * UTPBot — Puente con Firebase Auth (custom tokens).
 *
 * Se carga como <script type="module"> — los scripts de módulo se difieren
 * automáticamente y siempre terminan de ejecutarse ANTES del evento
 * DOMContentLoaded, así que tanto script.js como admin.js pueden asumir que
 * `window.UtpFirebase` ya existe dentro de cualquier callback de
 * DOMContentLoaded o de un manejador de clic (ambos ocurren después).
 *
 * Flujo (ver plan de migración, sección "Autenticación: Firebase Auth con
 * custom tokens"):
 *   1. POST /auth/login (backend) valida código+contraseña contra Postgres y
 *      devuelve un CUSTOM TOKEN de Firebase (campo "token" de la respuesta).
 *   2. El frontend canjea ese custom token por una sesión real de Firebase
 *      con signInWithCustomToken().
 *   3. Cada request al backend usa getIdToken() — un ID token real, que
 *      Firebase refresca solo cada hora — como "Authorization: Bearer ...".
 *   4. Efecto colateral: el SDK de Firebase persiste la sesión (IndexedDB) y
 *      la rehidrata sola al recargar la página vía onAuthStateChanged, lo
 *      que corrige el bug actual donde estudiantes/docentes pierden la
 *      sesión al refrescar (hoy solo el admin persiste, vía localStorage).
 */

import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.17.1/firebase-app.js';
import {
  getAuth,
  signInWithCustomToken,
  onAuthStateChanged,
  signOut,
} from 'https://www.gstatic.com/firebasejs/12.17.1/firebase-auth.js';

// Config pública del proyecto Firebase — estos valores NO son secretos (Firebase los
// expone deliberadamente en el cliente; la seguridad real la da el backend verificando
// el ID token con el Admin SDK, no esta config).
// Proyecto: utpbot-staging (obtenido con `firebase apps:sdkconfig WEB --project utpbot-staging`)
const firebaseConfig = {
  apiKey: 'AIzaSyDGjOFIgp0_Zi5A-AbQR64fjKZWrWQW7KM',
  authDomain: 'utpbot-staging.firebaseapp.com',
  projectId: 'utpbot-staging',
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);

/**
 * API mínima expuesta a script.js/admin.js (ambos scripts clásicos, no módulos).
 * Se cuelga de `window` porque los scripts clásicos no pueden hacer `import` directo.
 */
window.UtpFirebase = {
  /** Paso 2 del flujo: canjea el custom token del backend por una sesión real. */
  async signInWithCustomToken(customToken) {
    return signInWithCustomToken(auth, customToken);
  },

  /**
   * ID token vigente para usar como Bearer — SIEMPRE pedirlo justo antes de cada
   * request (no cachearlo en una variable de larga vida): el SDK lo refresca solo
   * cuando falta poco para expirar, sin coste de red si todavía es válido.
   */
  async getIdToken() {
    if (!auth.currentUser) return null;
    return auth.currentUser.getIdToken();
  },

  /** Claims completos del token vigente (codigo, rol, nombre, idioma) — para rehidratar sesión. */
  async getIdTokenClaims() {
    if (!auth.currentUser) return null;
    const result = await auth.currentUser.getIdTokenResult();
    return result.claims;
  },

  onAuthChange(callback) {
    return onAuthStateChanged(auth, callback);
  },

  async signOut() {
    return signOut(auth);
  },
};

window.dispatchEvent(new Event('utpfirebase-ready'));
