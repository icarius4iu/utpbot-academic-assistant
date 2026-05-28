/**
 * UTPBot — Admin Dashboard JavaScript
 * Panel de administración exclusivo para el rol 'admin'.
 * Gestiona autenticación, fetch de métricas y renderizado de charts.
 */

// ─── Config ──────────────────────────────────────────────────────────
const API_BASE = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
  ? 'http://localhost:8000'
  : 'https://web-production-e2e70.up.railway.app';  // ← Actualizar con URL real en producción

const REFRESH_INTERVAL_MS = 60_000; // Auto-refresh cada 60 segundos

// ─── Estado global ────────────────────────────────────────────────────
let _token = null;
let _chartCategory = null;
let _chartRole = null;
let _chartDaily = null;
let _logsData = [];
let _refreshTimer = null;

// ─── Paleta de colores para charts ───────────────────────────────────
const CHART_COLORS = [
  '#e8192c', '#3b82f6', '#a855f7', '#22c55e',
  '#f97316', '#06b6d4', '#eab308', '#ec4899'
];

const CHART_COLORS_ALPHA = CHART_COLORS.map(c => c + '33');

// ─── Inicialización ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  const token = localStorage.getItem('utpbot_token');
  const rol = localStorage.getItem('utpbot_rol');
  const nombre = localStorage.getItem('utpbot_nombre');

  if (!token || rol !== 'admin') {
    // No autorizado — redirigir al login
    window.location.href = './index.html';
    return;
  }

  _token = token;

  // Actualizar nombre en sidebar
  const nameEl = document.getElementById('admin-name-sidebar');
  if (nameEl) nameEl.textContent = nombre || 'Administrador';

  // Cargar datos y mostrar UI
  cargarDashboard();
  iniciarAutoRefresh();
});

// ─── Auto-refresh ──────────────────────────────────────────────────────
function iniciarAutoRefresh() {
  if (_refreshTimer) clearInterval(_refreshTimer);
  _refreshTimer = setInterval(cargarDashboard, REFRESH_INTERVAL_MS);
}

// ─── Logout ───────────────────────────────────────────────────────────
function doAdminLogout() {
  if (_refreshTimer) clearInterval(_refreshTimer);
  localStorage.removeItem('utpbot_token');
  localStorage.removeItem('utpbot_rol');
  localStorage.removeItem('utpbot_nombre');
  localStorage.removeItem('utpbot_codigo');
  window.location.href = './index.html';
}

// ─── Toggle sidebar móvil ─────────────────────────────────────────────
function toggleAdminSidebar() {
  document.getElementById('admin-sidebar').classList.toggle('open');
}

// ─── Navegación entre secciones ───────────────────────────────────────
const SECTION_TITLES = {
  overview: ['Dashboard de Métricas', 'Resumen general del sistema UTPBot'],
  activity: ['Actividad del Sistema', 'Consultas diarias durante los últimos 30 días'],
  logs: ['Consultas Recientes', 'Registro detallado de las últimas interacciones'],
  telegram: ['Integración Telegram', 'Gestión y configuración del bot de Telegram'],
};

function showSection(sectionId) {
  // Secciones
  document.querySelectorAll('.admin-section').forEach(s => s.classList.remove('active'));
  const target = document.getElementById(`section-${sectionId}`);
  if (target) target.classList.add('active');

  // Nav items
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  const navTarget = document.getElementById(`nav-${sectionId}`);
  if (navTarget) navTarget.classList.add('active');

  // Título
  const [title, subtitle] = SECTION_TITLES[sectionId] || ['', ''];
  document.getElementById('page-title').textContent = title;
  document.getElementById('page-subtitle').textContent = subtitle;

  // Cerrar sidebar en móvil
  document.getElementById('admin-sidebar').classList.remove('open');

  // Cargar datos de Telegram al entrar a esa sección
  if (sectionId === 'telegram') verificarTelegram();
}

// ─── Fetch con auth ───────────────────────────────────────────────────
async function apiFetch(endpoint) {
  const res = await fetch(`${API_BASE}${endpoint}`, {
    headers: {
      'Authorization': `Bearer ${_token}`,
      'Content-Type': 'application/json',
    },
  });

  if (res.status === 401 || res.status === 403) {
    doAdminLogout();
    throw new Error('No autorizado');
  }

  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

// ─── Cargar dashboard completo ────────────────────────────────────────
async function cargarDashboard() {
  const refreshIcon = document.getElementById('refresh-icon');
  const refreshBtn = document.querySelector('.btn-refresh');
  if (refreshBtn) refreshBtn.classList.add('spinning');

  try {
    const data = await apiFetch('/admin/dashboard');

    renderOverview(data.overview);
    renderBarsRol(data.overview);
    renderChartCategory(data.por_categoria);
    renderChartRole(data.por_rol);
    renderChartDaily(data.por_dia);
    renderLogs(data.recientes);

    // Mostrar UI (ocultar loader)
    document.getElementById('admin-loader').style.display = 'none';
    document.getElementById('admin-wrapper').style.display = 'flex';

    // Timestamp de última actualización
    const now = new Date();
    document.getElementById('last-update-text').textContent =
      `Actualizado: ${now.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}`;

  } catch (err) {
    console.error('Error cargando dashboard:', err);
    // Si hay error de red, mostrar UI de todas formas con datos vacíos
    document.getElementById('admin-loader').style.display = 'none';
    document.getElementById('admin-wrapper').style.display = 'flex';
    document.getElementById('last-update-text').textContent = '⚠️ Error al cargar datos';
    renderLogsEmpty('No se pudieron cargar los datos. Verifica la conexión con el backend.');
  } finally {
    if (refreshBtn) refreshBtn.classList.remove('spinning');
  }
}

// ─── Renderizar Overview (KPIs) ───────────────────────────────────────
function renderOverview(ov) {
  if (!ov) return;

  animateNumber('kpi-total-val', ov.total_consultas || 0);
  animateNumber('kpi-hoy-val', ov.consultas_hoy || 0);
  animateNumber('kpi-usuarios-val', ov.usuarios_activos || 0);

  const catEl = document.getElementById('kpi-categoria-val');
  if (catEl) catEl.textContent = capitalizar(ov.categoria_top || '—');
}

function renderBarsRol(ov) {
  if (!ov) return;
  const pctEst = ov.porcentaje_estudiantes || 0;
  const pctDoc = ov.porcentaje_docentes || 0;

  const elPctEst = document.getElementById('pct-estudiantes');
  const elPctDoc = document.getElementById('pct-docentes');
  const barEst = document.getElementById('bar-estudiantes');
  const barDoc = document.getElementById('bar-docentes');

  if (elPctEst) elPctEst.textContent = `${pctEst}%`;
  if (elPctDoc) elPctDoc.textContent = `${pctDoc}%`;

  // Animar barras con setTimeout para activar CSS transition
  setTimeout(() => {
    if (barEst) barEst.style.width = `${pctEst}%`;
    if (barDoc) barDoc.style.width = `${pctDoc}%`;
  }, 100);
}

// ─── Animación de números ─────────────────────────────────────────────
function animateNumber(elementId, target) {
  const el = document.getElementById(elementId);
  if (!el) return;

  const start = parseInt(el.textContent.replace(/\D/g, '')) || 0;
  const duration = 800;
  const startTime = performance.now();

  function update(now) {
    const elapsed = now - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3); // ease-out cubic
    const current = Math.round(start + (target - start) * eased);
    el.textContent = current.toLocaleString('es-PE');
    if (progress < 1) requestAnimationFrame(update);
  }

  requestAnimationFrame(update);
}

// ─── Chart: Categorías (Donut) ────────────────────────────────────────
function renderChartCategory(data) {
  const canvas = document.getElementById('chart-category');
  if (!canvas || !data || !data.length) return;

  if (_chartCategory) _chartCategory.destroy();

  _chartCategory = new Chart(canvas, {
    type: 'doughnut',
    data: {
      labels: data.map(d => capitalizar(d.categoria)),
      datasets: [{
        data: data.map(d => d.cantidad),
        backgroundColor: CHART_COLORS.slice(0, data.length),
        borderColor: '#0d0f14',
        borderWidth: 3,
        hoverOffset: 8,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '68%',
      plugins: {
        legend: {
          position: 'right',
          labels: {
            color: '#8892a4',
            font: { family: 'Inter', size: 11 },
            padding: 12,
            boxWidth: 12,
          }
        },
        tooltip: {
          callbacks: {
            label: ctx => ` ${ctx.label}: ${ctx.parsed} (${data[ctx.dataIndex]?.porcentaje || 0}%)`
          }
        }
      }
    }
  });
}

// ─── Chart: Roles (Barras) ────────────────────────────────────────────
function renderChartRole(data) {
  const canvas = document.getElementById('chart-role');
  if (!canvas || !data || !data.length) return;

  if (_chartRole) _chartRole.destroy();

  const colors = { estudiante: '#3b82f6', docente: '#a855f7', desconocido: '#505869' };

  _chartRole = new Chart(canvas, {
    type: 'bar',
    data: {
      labels: data.map(d => capitalizar(d.rol)),
      datasets: [{
        label: 'Consultas',
        data: data.map(d => d.cantidad),
        backgroundColor: data.map(d => colors[d.rol] || '#8892a4'),
        borderRadius: 8,
        borderSkipped: false,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: ctx => ` ${ctx.parsed.y} consultas`
          }
        }
      },
      scales: {
        x: { ticks: { color: '#8892a4', font: { family: 'Inter' } }, grid: { display: false } },
        y: {
          ticks: { color: '#8892a4', font: { family: 'Inter' }, stepSize: 1 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        }
      }
    }
  });
}

// ─── Chart: Actividad diaria (Línea) ─────────────────────────────────
function renderChartDaily(data) {
  const canvas = document.getElementById('chart-daily');
  if (!canvas || !data || !data.length) return;

  if (_chartDaily) _chartDaily.destroy();

  // Formatear fechas para labels
  const labels = data.map(d => {
    const [y, m, dia] = d.fecha.split('-');
    return `${dia}/${m}`;
  });

  _chartDaily = new Chart(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Consultas',
        data: data.map(d => d.cantidad),
        borderColor: '#e8192c',
        backgroundColor: 'rgba(232, 25, 44, 0.1)',
        borderWidth: 2.5,
        fill: true,
        tension: 0.4,
        pointRadius: 3,
        pointBackgroundColor: '#e8192c',
        pointHoverRadius: 6,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: ctx => ` ${ctx.parsed.y} consultas`
          }
        }
      },
      scales: {
        x: {
          ticks: {
            color: '#8892a4',
            font: { family: 'Inter', size: 11 },
            maxTicksLimit: 15,
          },
          grid: { color: 'rgba(255,255,255,0.04)' }
        },
        y: {
          ticks: { color: '#8892a4', font: { family: 'Inter' }, stepSize: 1 },
          grid: { color: 'rgba(255,255,255,0.05)' },
          beginAtZero: true,
        }
      }
    }
  });
}

// ─── Logs table ───────────────────────────────────────────────────────
function renderLogs(logs) {
  _logsData = logs || [];
  renderLogsTabla(_logsData);
}

function renderLogsTabla(logs) {
  const tbody = document.getElementById('logs-tbody');
  if (!tbody) return;

  if (!logs || !logs.length) {
    renderLogsEmpty('No hay consultas registradas aún.');
    return;
  }

  tbody.innerHTML = logs.map(log => `
    <tr>
      <td class="date-cell">${formatFecha(log.fecha)}</td>
      <td class="code-cell">${escapeHtml(log.codigo_usuario || '—')}</td>
      <td><span class="role-pill ${log.rol || ''}">${capitalizar(log.rol || '—')}</span></td>
      <td><span class="cat-pill">${capitalizar(log.categoria || 'general')}</span></td>
      <td class="question-cell" title="${escapeHtml(log.pregunta || '')}">${escapeHtml(log.pregunta || '—')}</td>
    </tr>
  `).join('');
}

function renderLogsEmpty(msg) {
  const tbody = document.getElementById('logs-tbody');
  if (tbody) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5">
          <div class="empty-state">
            <i class="ph ph-chat-slash"></i>
            <p>${msg}</p>
          </div>
        </td>
      </tr>`;
  }
}

function filtrarLogs() {
  const query = (document.getElementById('search-logs')?.value || '').toLowerCase().trim();
  if (!query) {
    renderLogsTabla(_logsData);
    return;
  }
  const filtrados = _logsData.filter(l =>
    (l.pregunta || '').toLowerCase().includes(query) ||
    (l.categoria || '').toLowerCase().includes(query) ||
    (l.codigo_usuario || '').toLowerCase().includes(query)
  );
  renderLogsTabla(filtrados);
}

// ─── Telegram ─────────────────────────────────────────────────────────
async function verificarTelegram() {
  try {
    const data = await apiFetch('/telegram/status');
    setBadge('tg-token-status', data.token_configurado ? 'ok' : 'error', data.token_configurado ? '✓ Sí' : '✗ No');
    setBadge('tg-webhook-status', data.webhook_configurado ? 'ok' : 'error', data.webhook_configurado ? '✓ Activo' : '✗ Inactivo');
    setBadge('tg-overall-status', data.listo ? 'ok' : 'pending', data.listo ? '✅ Listo' : '⚠️ Pendiente');

    const urlEl = document.getElementById('tg-webhook-url');
    if (urlEl) urlEl.textContent = data.webhook_url || '—';
  } catch (err) {
    setBadge('tg-token-status', 'error', 'Error');
    setBadge('tg-webhook-status', 'error', 'Error');
    setBadge('tg-overall-status', 'error', '✗ Error');
  }
}

async function activarWebhook() {
  const btn = document.getElementById('btn-activate-webhook');
  const msg = document.getElementById('tg-setup-msg');
  if (!btn || !msg) return;

  btn.disabled = true;
  btn.innerHTML = '<div class="mini-spinner"></div> Configurando...';
  msg.textContent = '';
  msg.style.color = '';

  try {
    const data = await apiFetch('/telegram/setup-webhook');
    if (data.success) {
      msg.textContent = '✅ Webhook activado correctamente.';
      msg.style.color = '#22c55e';
      verificarTelegram();
    } else {
      throw new Error(data.detail || 'Error desconocido');
    }
  } catch (err) {
    const errMsg = err.message || 'Error al activar el webhook';
    msg.textContent = `❌ ${errMsg}`;
    msg.style.color = '#e8192c';
  } finally {
    btn.disabled = false;
    btn.innerHTML = '<i class="ph ph-plug"></i> Activar Webhook';
  }
}

function setBadge(elementId, type, text) {
  const el = document.getElementById(elementId);
  if (!el) return;
  el.className = `status-badge ${type}`;
  if (elementId === 'tg-overall-status') el.className += ' status-large';
  el.textContent = text;
}

// ─── Utilidades ───────────────────────────────────────────────────────
function capitalizar(str) {
  if (!str) return '—';
  return str.charAt(0).toUpperCase() + str.slice(1);
}

function escapeHtml(str) {
  if (!str) return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatFecha(fechaStr) {
  if (!fechaStr) return '—';
  try {
    const d = new Date(fechaStr);
    return d.toLocaleString('es-PE', {
      day: '2-digit', month: '2-digit', year: '2-digit',
      hour: '2-digit', minute: '2-digit'
    });
  } catch {
    return fechaStr.substring(0, 16);
  }
}
