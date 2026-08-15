/**
 * UTPBot Sync — inject.js (corre en el MAIN world de class.utp.edu.pe)
 *
 * Dos responsabilidades:
 *  1. CAPTURA PASIVA: observa los headers que la SPA del portal ya manda en sus
 *     propias llamadas (token Keycloak, User-Id, X-Tenant-Id). Nunca toca la
 *     contraseña del alumno — solo mira requests que la app hace por su cuenta.
 *  2. REPLAY: a pedido del popup, re-consulta los 3 endpoints de api-pao DESDE
 *     ESTE MISMO ORIGIN (por eso corre acá y no en el service worker: la API
 *     rechaza por CORS cualquier request que no venga de class.utp.edu.pe) y
 *     arma el JSON del contrato de /estudiante/sincronizar.
 *
 * Ver etl/UTPBotSync_GuiaImplementacion.md §2, §3 y §4.
 */
(function () {
  "use strict";

  const API = "https://api-pao.utpxpedition.com";
  const creds = { token: null, userId: null, tenantId: null };

  // ─────────── 1. Captura pasiva de credenciales ───────────
  function remember(headers) {
    if (!headers) return;
    const auth = headers["authorization"];
    if (auth && /^Bearer /.test(auth)) creds.token = auth;
    if (headers["user-id"]) creds.userId = headers["user-id"];
    if (headers["x-tenant-id"]) creds.tenantId = headers["x-tenant-id"];
  }

  // El portal usa Angular HttpClient → XHR
  const _open = XMLHttpRequest.prototype.open;
  const _setHeader = XMLHttpRequest.prototype.setRequestHeader;
  const _send = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function (method, url) {
    this.__utpUrl = url;
    this.__utpHeaders = {};
    return _open.apply(this, arguments);
  };
  XMLHttpRequest.prototype.setRequestHeader = function (key, value) {
    try { this.__utpHeaders[String(key).toLowerCase()] = value; } catch (e) { /* ignorar */ }
    return _setHeader.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    if (this.__utpUrl && /utpxpedition\.com/.test(this.__utpUrl)) remember(this.__utpHeaders);
    return _send.apply(this, arguments);
  };

  // Por si alguna llamada usa fetch en vez de XHR
  const _fetch = window.fetch;
  window.fetch = function (input, init) {
    try {
      const url = typeof input === "string" ? input : (input && input.url);
      if (url && /utpxpedition\.com/.test(url)) {
        const h = {};
        if (init && init.headers) new Headers(init.headers).forEach((v, k) => (h[k.toLowerCase()] = v));
        remember(h);
      }
    } catch (e) { /* ignorar */ }
    return _fetch.apply(this, arguments);
  };

  // ─────────── 2. Llamadas a la API del portal ───────────
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

  // ─────────── 3. Transformaciones (guía §4) ───────────
  const MODALIDAD = { P: "Presencial", VT: "Virtual 24/7", V: "Virtual", VS: "Virtual", R: "Regular" };
  const DIAS = ["Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];

  const pad = (n) => String(n).padStart(2, "0");

  function horaDe(fechaHora) {
    const parte = String(fechaHora).split(" ")[1] || "";
    const [h, m] = parte.split(":");
    return h && m ? pad(h) + ":" + pad(m) : null;
  }

  function diaDe(fechaHora) {
    const [Y, M, D] = String(fechaHora).split(" ")[0].split("-").map(Number);
    return DIAS[new Date(Y, M - 1, D).getDay()];
  }

  function decodeJwt(bearer) {
    try {
      const payload = String(bearer).replace(/^Bearer /, "").split(".")[1];
      const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
      // decodeURIComponent+escape para que los acentos del nombre no se rompan
      return JSON.parse(decodeURIComponent(escape(json)));
    } catch (e) {
      return {};
    }
  }

  /**
   * Elige el ciclo actual. No usa solo `type == CURRENT_PERIOD` porque el bucket
   * THP (period "9999", acadCareer "PRED") también viene marcado así — ver §2.5.
   */
  function elegirCicloActual(periodsData) {
    const lista = (periodsData && periodsData.academicPeriods) || [];
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    const enRango = (p) =>
      p.start && p.end && new Date(p.start) <= hoy && hoy <= new Date(p.end + "T23:59:59");

    return lista.find((p) => p.acadCareer !== "PRED" && enRango(p))
        || lista.find((p) => p.type === "CURRENT_PERIOD" && p.acadCareer !== "PRED")
        || lista.filter((p) => p.acadCareer !== "PRED")
                .sort((a, b) => (b.start || "").localeCompare(a.start || ""))[0]
        || null;
  }

  // ─────────── 4. Armado del payload ───────────
  async function buildPayload() {
    if (!creds.token || !creds.userId) {
      throw new Error('Todavía no capturé el token del portal. Entrá a "Cursos" o "Calendario" en el portal y reintentá.');
    }

    const studentId = creds.userId;
    const [cursosRaw, calendario, periodos] = await Promise.all([
      api("/learning/student/" + studentId + "/dashboard-courses"),
      api("/course/student/calendar"),
      api("/course/student/" + studentId + "/academicperiods")
    ]);

    const todos = Array.isArray(cursosRaw) ? cursosRaw : [];
    const ciclo = elegirCicloActual(periodos);
    const cicloCodigo = ciclo ? ciclo.period : null;
    const cicloNombre = (ciclo && ciclo.name)
      || (calendario && calendario.current_interval && calendario.current_interval.period_name)
      || null;

    const delCiclo = todos.filter((c) => (cicloCodigo ? c.period === cicloCodigo : c.active));
    const porCourseId = {};
    delCiclo.forEach((c) => (porCourseId[c.courseId] = c));

    const cursos = delCiclo.map((c) => ({
      codigo_curso: c.classNumber,
      nombre_curso: c.name,
      modalidad: MODALIDAD[c.modality] || c.modality || null,
      docente: [c.teacherFirstName, c.teacherLastName].filter(Boolean).join(" ").trim() || null,
      progreso_porcentaje: Math.round(c.progress || 0)
    }));

    // El calendario trae TODAS las semanas del ciclo → deduplicar al patrón semanal
    const eventos = (calendario && calendario.current_interval && calendario.current_interval.events) || [];
    const vistos = new Set();
    const horarios = [];

    eventos.filter((e) => e.type === "CLASS").forEach((e) => {
      const curso = porCourseId[e.metadata && e.metadata.courseId];
      const dia = diaDe(e.startAt);
      const horaInicio = horaDe(e.startAt);
      const horaFin = horaDe(e.finishAt);
      const codigo = curso ? curso.classNumber : null;

      const clave = codigo + "|" + dia + "|" + horaInicio + "|" + horaFin;
      if (vistos.has(clave)) return;
      vistos.add(clave);

      horarios.push({
        codigo_curso: codigo,
        dia: dia,
        hora_inicio: horaInicio,
        hora_fin: horaFin,
        modalidad: MODALIDAD[e.modality] || e.modality || null,
        aula: curso ? (curso.classroom || null) : null
      });
    });

    const jwt = decodeJwt(creds.token);
    return {
      perfil: {
        codigo: jwt.preferred_username || null,
        nombre: jwt.name || null,
        carrera: null, // pendiente: el portal no lo expone en estos endpoints (guía §9)
        ciclo: cicloNombre,
        ciclo_codigo: cicloCodigo
      },
      cursos: cursos,
      horarios: horarios
    };
  }

  // ─────────── 5. Puente con content.js ───────────
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
