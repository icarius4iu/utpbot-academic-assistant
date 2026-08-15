/**
 * UTPBot Sync — content.js (ISOLATED world)
 *
 * Puente entre la página y la extensión. Inyecta inject.js en el MAIN world (donde
 * puede observar el fetch/XHR de la SPA y re-consultar api-pao desde el mismo origin)
 * y traduce entre window.postMessage y chrome.runtime.
 */
(function () {
  const script = document.createElement("script");
  script.src = chrome.runtime.getURL("inject.js");
  script.onload = () => script.remove();
  (document.head || document.documentElement).appendChild(script);
})();

const pendientes = {};

window.addEventListener("message", (ev) => {
  if (ev.source !== window || !ev.data || ev.data.__utpbot !== "res") return;
  const callback = pendientes[ev.data.id];
  if (callback) {
    callback(ev.data);
    delete pendientes[ev.data.id];
  }
});

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg && msg.type === "UTPBOT_BUILD") {
    const id = Math.random().toString(36).slice(2);
    pendientes[id] = (data) => sendResponse(data);
    window.postMessage({ __utpbot: "req", id: id }, "*");
    return true; // respuesta asíncrona
  }
});
