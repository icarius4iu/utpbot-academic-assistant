/**
 * UTP IA — Módulo de estudio personalizado (frontend).
 *
 * Cubre las 3 historias:
 *   1. Subir sílabo → ruta de estudio generada por IA
 *   2. Subir materiales → cuestionarios y resúmenes contextuados
 *   3. Meta diaria en minutos + racha
 *
 * Reusa API_URL y window.UtpFirebase de script.js (mismo scope global), así que
 * este archivo debe cargarse DESPUÉS de script.js.
 */

// ─────────── Helpers ───────────

/** Fetch autenticado: pide un ID token fresco a Firebase en cada llamada. */
async function estudioFetch(ruta, opciones = {}) {
  const idToken = await window.UtpFirebase.getIdToken();
  const resp = await fetch(`${API_URL}${ruta}`, {
    ...opciones,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${idToken}`,
      ...(opciones.headers || {})
    }
  });
  const texto = await resp.text();
  let data;
  try { data = texto ? JSON.parse(texto) : null; } catch (e) { data = texto; }
  if (!resp.ok) {
    throw new Error((data && data.detail) || `Error ${resp.status}`);
  }
  return data;
}

function estudioMsg(id, texto, tipo = '') {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = texto;
  el.className = 'est-muted' + (tipo ? ' est-' + tipo : '');
}

function escaparHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// ─────────── HISTORIA 3: racha y meta ───────────

async function cargarRacha() {
  try {
    const d = await estudioFetch('/estudio/racha');
    document.getElementById('racha-dias').textContent = d.racha_actual;

    const pct = d.minutos_diarios > 0
      ? Math.min(100, Math.round((d.minutos_hoy / d.minutos_diarios) * 100)) : 0;
    document.getElementById('racha-barra').style.width = pct + '%';
    document.getElementById('racha-progreso-texto').textContent =
      `${d.minutos_hoy} / ${d.minutos_diarios} min hoy` + (d.meta_cumplida_hoy ? ' ✓' : '');

    const meta = document.getElementById('input-meta');
    if (meta && !meta.value) meta.placeholder = `meta: ${d.minutos_diarios} min`;

    // Calendario de los últimos 7 días
    const dias = ['D', 'L', 'M', 'M', 'J', 'V', 'S'];
    document.getElementById('racha-semana').innerHTML = (d.ultimos7_dias || []).map(x => {
      const [Y, M, D] = x.fecha.split('-').map(Number);
      const inicial = dias[new Date(Y, M - 1, D).getDay()];
      return `<div class="est-dia ${x.cumplida ? 'ok' : ''}" title="${x.fecha}: ${x.minutos} min">
                <span>${inicial}</span></div>`;
    }).join('');

    if (d.mejor_racha > 0) {
      estudioMsg('racha-msg', `Mejor racha: ${d.mejor_racha} días · Esta semana: ${d.minutos_semana} min`);
    }
  } catch (e) {
    estudioMsg('racha-msg', e.message, 'err');
  }
}

async function registrarSesion() {
  const input = document.getElementById('input-minutos');
  const minutos = parseInt(input.value, 10);
  if (!minutos || minutos < 1) {
    estudioMsg('racha-msg', 'Ingresá cuántos minutos estudiaste.', 'err');
    return;
  }
  try {
    estudioMsg('racha-msg', 'Registrando…');
    await estudioFetch('/estudio/sesiones', {
      method: 'POST',
      body: JSON.stringify({ minutos })
    });
    input.value = '';
    await cargarRacha();
    estudioMsg('racha-msg', `¡Listo! Sumaste ${minutos} min.`, 'ok');
  } catch (e) {
    estudioMsg('racha-msg', e.message, 'err');
  }
}

async function guardarMeta() {
  const input = document.getElementById('input-meta');
  const minutos = parseInt(input.value, 10);
  if (!minutos) {
    estudioMsg('racha-msg', 'Ingresá tu meta diaria en minutos.', 'err');
    return;
  }
  try {
    await estudioFetch('/estudio/meta', {
      method: 'PUT',
      body: JSON.stringify({ minutos_diarios: minutos })
    });
    input.value = '';
    await cargarRacha();
    estudioMsg('racha-msg', `Meta fijada en ${minutos} min diarios.`, 'ok');
  } catch (e) {
    estudioMsg('racha-msg', e.message, 'err');
  }
}

// ─────────── HISTORIAS 1 y 2: materiales ───────────

async function subirMaterial() {
  const input = document.getElementById('input-archivo-estudio');
  const archivo = input.files && input.files[0];
  if (!archivo) {
    estudioMsg('material-msg', 'Elegí un archivo primero.', 'err');
    return;
  }
  // 15 MB es el límite del backend (quarkus.http.limits.max-body-size); base64 infla ~33%
  if (archivo.size > 10 * 1024 * 1024) {
    estudioMsg('material-msg', 'El archivo supera los 10 MB.', 'err');
    return;
  }

  try {
    estudioMsg('material-msg', 'Leyendo archivo…');
    const base64 = await new Promise((resolve, reject) => {
      const fr = new FileReader();
      fr.onload = () => resolve(String(fr.result).split(',')[1]);
      fr.onerror = reject;
      fr.readAsDataURL(archivo);
    });

    estudioMsg('material-msg', 'Subiendo y extrayendo texto…');
    const material = await estudioFetch('/estudio/materiales', {
      method: 'POST',
      body: JSON.stringify({
        nombre_archivo: archivo.name,
        mime_type: archivo.type,
        file_data: base64,
        tipo: document.getElementById('select-tipo-material').value,
        codigo_curso: document.getElementById('input-curso-material').value.trim() || null
      })
    });

    input.value = '';
    estudioMsg('material-msg', `"${material.nombre_archivo}" subido (${material.caracteres} caracteres).`, 'ok');
    await cargarMateriales();
  } catch (e) {
    estudioMsg('material-msg', e.message, 'err');
  }
}

async function cargarMateriales() {
  try {
    const lista = await estudioFetch('/estudio/materiales');
    const cont = document.getElementById('lista-materiales');
    if (!lista.length) {
      cont.innerHTML = '<p class="est-muted">Todavía no subiste nada.</p>';
      return;
    }
    cont.innerHTML = lista.map(m => `
      <div class="est-item">
        <div class="est-item-top">
          <strong>${escaparHtml(m.nombre_archivo)}</strong>
          <span class="est-chip">${m.tipo === 'SILABO' ? 'Sílabo' : 'Material'}</span>
        </div>
        <div class="est-acciones">
          ${m.tipo === 'SILABO'
            ? `<button class="est-btn est-btn-mini" onclick="generarRuta(${m.id})">Generar ruta</button>`
            : ''}
          <button class="est-btn est-btn-mini" onclick="generarCuestionario(${m.id})">Cuestionario</button>
          <button class="est-btn est-btn-mini est-btn-sec" onclick="generarResumen(${m.id})">Resumen</button>
        </div>
      </div>`).join('');
  } catch (e) {
    estudioMsg('material-msg', e.message, 'err');
  }
}

// ─────────── HISTORIA 1: ruta de estudio ───────────

async function generarRuta(materialId) {
  try {
    estudioMsg('material-msg', 'La IA está armando tu ruta de estudio… (puede tardar)');
    await estudioFetch(`/estudio/materiales/${materialId}/ruta`, { method: 'POST' });
    estudioMsg('material-msg', 'Ruta generada.', 'ok');
    await cargarRutas();
  } catch (e) {
    estudioMsg('material-msg', e.message, 'err');
  }
}

async function cargarRutas() {
  try {
    const rutas = await estudioFetch('/estudio/rutas');
    const cont = document.getElementById('lista-rutas');
    if (!rutas.length) {
      cont.innerHTML = '<p class="est-muted">Sin rutas todavía. Subí un sílabo para generar una.</p>';
      return;
    }
    cont.innerHTML = rutas.map(r => {
      const pct = r.total_temas ? Math.round((r.temas_completados / r.total_temas) * 100) : 0;
      return `
        <div class="est-item est-clickable" onclick="verRuta(${r.id})">
          <div class="est-item-top"><strong>${escaparHtml(r.curso)}</strong></div>
          <div class="est-muted">${r.temas_completados}/${r.total_temas} temas · ${pct}%</div>
          <div class="est-barra"><div class="est-barra-fill" style="width:${pct}%"></div></div>
        </div>`;
    }).join('');
  } catch (e) {
    console.error(e);
  }
}

async function verRuta(rutaId) {
  try {
    const r = await estudioFetch(`/estudio/rutas/${rutaId}`);
    const temas = r.temas.map(t => `
      <label class="est-tema ${t.completado ? 'hecho' : ''}">
        <input type="checkbox" ${t.completado ? 'checked' : ''}
               onchange="marcarTema(${t.id}, this.checked, ${rutaId})" />
        <div>
          <strong>${t.orden}. ${escaparHtml(t.titulo)}</strong>
          ${t.horas_estimadas ? `<span class="est-chip">${t.horas_estimadas}h</span>` : ''}
          <div class="est-muted">${escaparHtml(t.descripcion || '')}</div>
        </div>
      </label>`).join('');

    abrirModalEstudio(escaparHtml(r.titulo),
      `<p class="est-muted">${escaparHtml(r.descripcion || '')}</p>
       <div class="est-temas">${temas}</div>`);
  } catch (e) {
    alert(e.message);
  }
}

async function marcarTema(temaId, completado, rutaId) {
  try {
    await estudioFetch(`/estudio/temas/${temaId}`, {
      method: 'PATCH',
      body: JSON.stringify({ completado })
    });
    await cargarRutas();
  } catch (e) {
    alert(e.message);
  }
}

// ─────────── HISTORIA 2: cuestionarios y resúmenes ───────────

async function generarCuestionario(materialId) {
  try {
    estudioMsg('material-msg', 'Generando cuestionario con IA…');
    const c = await estudioFetch(`/estudio/materiales/${materialId}/cuestionario?preguntas=8`, { method: 'POST' });
    estudioMsg('material-msg', 'Cuestionario listo.', 'ok');
    mostrarCuestionario(c);
  } catch (e) {
    estudioMsg('material-msg', e.message, 'err');
  }
}

function mostrarCuestionario(c) {
  const html = c.preguntas.map((p, i) => `
    <div class="est-pregunta" data-correcta="${p.indice_correcto}" data-idx="${i}">
      <strong>${i + 1}. ${escaparHtml(p.enunciado)}</strong>
      <div class="est-opciones">
        ${p.opciones.map((o, j) => `
          <button class="est-opcion" onclick="responder(${i}, ${j}, ${p.indice_correcto})">
            ${String.fromCharCode(65 + j)}) ${escaparHtml(o)}
          </button>`).join('')}
      </div>
      <div class="est-explicacion" id="expl-${i}" hidden>
        ${escaparHtml(p.explicacion || '')}
      </div>
    </div>`).join('');

  abrirModalEstudio(escaparHtml(c.titulo), html);
}

/** Autoevaluación: marca la elegida y revela la correcta + explicación. */
function responder(idxPregunta, idxElegida, idxCorrecta) {
  const cont = document.querySelector(`.est-pregunta[data-idx="${idxPregunta}"]`);
  if (!cont || cont.dataset.respondida === '1') return;
  cont.dataset.respondida = '1';

  cont.querySelectorAll('.est-opcion').forEach((btn, j) => {
    if (j === idxCorrecta) btn.classList.add('correcta');
    else if (j === idxElegida) btn.classList.add('incorrecta');
    btn.disabled = true;
  });
  const expl = document.getElementById(`expl-${idxPregunta}`);
  if (expl) expl.hidden = false;
}

async function generarResumen(materialId) {
  try {
    estudioMsg('material-msg', 'Generando resumen con IA…');
    const r = await estudioFetch(`/estudio/materiales/${materialId}/resumen`, { method: 'POST' });
    estudioMsg('material-msg', 'Resumen listo.', 'ok');
    abrirModalEstudio('Resumen', `<pre class="est-resumen">${escaparHtml(r.contenido)}</pre>`);
  } catch (e) {
    estudioMsg('material-msg', e.message, 'err');
  }
}

// ─────────── Modal ───────────

function abrirModalEstudio(titulo, htmlContenido) {
  let modal = document.getElementById('modal-estudio');
  if (!modal) {
    modal = document.createElement('div');
    modal.id = 'modal-estudio';
    modal.className = 'est-modal';
    modal.innerHTML = `
      <div class="est-modal-card">
        <div class="est-modal-head">
          <h3 id="modal-estudio-titulo"></h3>
          <button class="icon-btn" onclick="cerrarModalEstudio()" aria-label="Cerrar">✕</button>
        </div>
        <div class="est-modal-body" id="modal-estudio-body"></div>
      </div>`;
    document.body.appendChild(modal);
    modal.addEventListener('click', (e) => { if (e.target === modal) cerrarModalEstudio(); });
  }
  document.getElementById('modal-estudio-titulo').textContent = titulo;
  document.getElementById('modal-estudio-body').innerHTML = htmlContenido;
  modal.classList.add('show');
}

function cerrarModalEstudio() {
  const m = document.getElementById('modal-estudio');
  if (m) m.classList.remove('show');
}

// ─────────── Carga inicial (al entrar a la pestaña) ───────────

let estudioCargado = false;

function inicializarEstudio() {
  if (estudioCargado) return;
  estudioCargado = true;
  cargarRacha();
  cargarMateriales();
  cargarRutas();
}
