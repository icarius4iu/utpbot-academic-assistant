// =============================================
//  CONFIGURACIÓN GLOBAL Y ESTADOS
// =============================================
// API URL — local en desarrollo, producción en deploy
const API_URL = (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')
  ? 'http://127.0.0.1:8000'
  : 'https://web-production-e2e70.up.railway.app'; // ← Actualizar con URL real del backend
let session = null;
let historialActual = []; // Mensajes de la sesión activa
let historialGlobal = []; // Todas las conversaciones anteriores guardadas
let currentSessionId = null;

// Archivo adjunto
let currentFileName = null;
let currentFileMime = null;
let currentFileData = null;

let currentLang = 'es';
let isDark = false;

// =============================================
//  DICCIONARIO ICONOS Y TEXTOS
// =============================================
const i18n = {
  es: {
    'login.subtitle': 'Asistente Académico Inteligente',
    'login.codigo': 'Identificador Institucional',
    'login.password': 'Contraseña de acceso',
    'login.btn': 'Ingresar al sistema',
    'chat.logout': 'Desconectar',
    'chat.placeholder': 'Escriba su consulta académica a la IA o suba un archivo...',
    'chat.header.title': 'Asistente UTP Virtual',
    'chat.header.subtitle': 'Centro de Soporte Académico',
    'chat.newchat': 'Nuevo Chat',
    'sidebar.tab1': 'Consultas Ágiles',
    'sidebar.tab2': 'Historial',
    'sidebar.title': 'Consultas Sugeridas',
    'role.estudiante': 'Estudiante UTP',
    'role.docente': 'Cuerpo Docente',
    'settings.title': 'Configuración',
    'settings.lang': 'Idioma de la Interfaz',
    'settings.theme': 'Apariencia Visual',
    'settings.light': 'Claro',
    'settings.dark': 'Oscuro',
    'settings.delete': 'Eliminar todo el historial',
    'q.horario': { text: 'Mi horario académico', icon: 'ph-calendar-blank' },
    'q.examen': { text: 'Próximas evaluaciones', icon: 'ph-exam' },
    'q.notas': { text: 'Rendimiento y notas', icon: 'ph-chart-bar' },
    'q.trabajos': { text: 'Asignaciones pendientes', icon: 'ph-push-pin' },
    'q.asistencia': { text: 'Registro de asistencia', icon: 'ph-check-circle' },
    'q.d.horario': { text: 'Horario de impartición', icon: 'ph-calendar-blank' },
    'q.d.alumnos': { text: 'Listado de secciones', icon: 'ph-users' },
    'q.d.asistencia': { text: 'Alertas de inasistencia', icon: 'ph-warning' },
    'q.d.notas': { text: 'Registros de notas', icon: 'ph-chart-bar' }
  },
  en: {
    'login.subtitle': 'Smart Academic Assistant',
    'login.codigo': 'Institutional Identifier',
    'login.password': 'Access Password',
    'login.btn': 'Sign In',
    'chat.logout': 'Sign Out',
    'chat.placeholder': 'Type your academic query or upload a file...',
    'chat.header.title': 'Virtual UTP Assistant',
    'chat.header.subtitle': 'Academic Support Center',
    'chat.newchat': 'New Chat',
    'sidebar.tab1': 'Quick Queries',
    'sidebar.tab2': 'History',
    'sidebar.title': 'Suggested Queries',
    'role.estudiante': 'UTP Student',
    'role.docente': 'Faculty Member',
    'settings.title': 'Settings',
    'settings.lang': 'Interface Language',
    'settings.theme': 'Visual Appearance',
    'settings.light': 'Light',
    'settings.dark': 'Dark',
    'settings.delete': 'Delete all history',
    'q.horario': { text: 'My academic schedule', icon: 'ph-calendar-blank' },
    'q.examen': { text: 'Upcoming assessments', icon: 'ph-exam' },
    'q.notas': { text: 'Grades and performance', icon: 'ph-chart-bar' },
    'q.trabajos': { text: 'Pending assignments', icon: 'ph-push-pin' },
    'q.asistencia': { text: 'Attendance record', icon: 'ph-check-circle' },
    'q.d.horario': { text: 'Teaching schedule', icon: 'ph-calendar-blank' },
    'q.d.alumnos': { text: 'Section rosters', icon: 'ph-users' },
    'q.d.asistencia': { text: 'Absence alerts', icon: 'ph-warning' },
    'q.d.notas': { text: 'Grade registers', icon: 'ph-chart-bar' }
  }
};

// Obtiene el texto traducido
function t(key) { return i18n[currentLang][key] || key; }

// Cambia el idioma de la página
function setLang(lang) {
  currentLang = lang;
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const k = el.getAttribute('data-i18n');
    if (i18n[lang][k]) el.textContent = i18n[lang][k];
  });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
    const k = el.getAttribute('data-i18n-placeholder');
    if (i18n[lang][k]) el.placeholder = i18n[lang][k];
  });
  document.querySelectorAll('.lang-btn').forEach(b => b.classList.remove('active'));
  let loginLangBtn = document.getElementById(`btn-lang-${lang}`);
  if (loginLangBtn) loginLangBtn.classList.add('active');

  if (session) {
    session.idioma = lang; // Actualizamos el del backend/session global
    document.getElementById('sidebar-rol').textContent = session.rol === 'estudiante' ? t('role.estudiante') : t('role.docente');
    buildQuickButtons();
  }
}

// =============================================
//  TEMA OSCURO
// =============================================
function toggleDark() {
  isDark = !isDark;
  document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
  document.querySelector('#btn-dark i').className = isDark ? 'ph ph-sun' : 'ph ph-moon';
}

// =============================================
//  GESTIÓN DE CHAT Y ARCHIVOS
// =============================================
function startNewChat() {
  if (historialActual.length > 0) {
    saveCurrentSessionToHistory();
  }
  currentSessionId = Date.now().toString();
  historialActual = [];
  document.getElementById('messages-area').innerHTML = '';
  document.getElementById('chat-input').value = '';
  clearFile();

  if (session) {
    let bienvenidaText = '';
    if (currentLang === 'es') {
      bienvenidaText = session.rol === 'estudiante'
        ? `**Saludos cordiales, ${session.nombre}.**\nSoy el motor de inteligencia artificial académica de la UTP. Mi base de conocimientos está conectada a sus registros. ¿Qué información requiere consultar hoy?`
        : `**Estimado/a Docente ${session.nombre},**\nEl sistema de asistencia de IA académica está en línea y conectado a sus registros. ¿Qué información de sus secciones requiere analizar?`;
    } else {
      bienvenidaText = session.rol === 'estudiante'
        ? `**Warm greetings, ${session.nombre}.**\nI am the UTP academic artificial intelligence engine. My knowledge base is connected to your records. What information do you need to consult today?`
        : `**Dear Faculty Member ${session.nombre},**\nThe academic AI assistance system is online and connected to your records. What section information do you need to analyze?`;
    }
    appendBotMsg(bienvenidaText, false);
  }
}

function handleFileUpload(event) {
  const file = event.target.files[0];
  if (!file) return;

  currentFileName = file.name;
  currentFileMime = file.type || "application/octet-stream";

  const reader = new FileReader();
  reader.onload = function (e) {
    const dataUrl = e.target.result;
    currentFileData = dataUrl.split(',')[1] || dataUrl; // Extraer el base64

    document.getElementById('file-preview-name').innerText = currentFileName;
    document.getElementById('file-preview-container').style.display = 'flex';
  };
  reader.readAsDataURL(file);
}

function clearFile() {
  currentFileName = null;
  currentFileMime = null;
  currentFileData = null;
  document.getElementById('file-input').value = "";
  document.getElementById('file-preview-container').style.display = 'none';
}

// =============================================
//  CONFIGURACIÓN / AJUSTES
// =============================================
function openSettings() {
  document.getElementById('settings-modal').classList.add('show');

  // Reflejar estado visual
  document.getElementById('opt-lang-es').classList.toggle('active', currentLang === 'es');
  document.getElementById('opt-lang-en').classList.toggle('active', currentLang === 'en');
  document.getElementById('opt-theme-light').classList.toggle('active', !isDark);
  document.getElementById('opt-theme-dark').classList.toggle('active', isDark);
}

function closeSettings(e) {
  if (e.target.id === 'settings-modal') {
    closeSettingsForce();
  }
}

function closeSettingsForce() {
  document.getElementById('settings-modal').classList.remove('show');
}

function setLangOpt(lang) {
  setLang(lang);
  document.getElementById('opt-lang-es').classList.toggle('active', lang === 'es');
  document.getElementById('opt-lang-en').classList.toggle('active', lang === 'en');
}

function setThemeOpt(theme) {
  const wantsDark = (theme === 'dark');
  if (wantsDark !== isDark) {
    toggleDark();
  }
  document.getElementById('opt-theme-light').classList.toggle('active', !isDark);
  document.getElementById('opt-theme-dark').classList.toggle('active', isDark);
}

function clearAllHistory() {
  if (!confirm('¿Estás seguro de que quieres eliminar TODO tu historial de chats? Esta acción no se puede deshacer.')) return;

  historialGlobal = [];
  localStorage.setItem(`utpbot_hist_v2_${session.codigo}`, JSON.stringify([]));
  startNewChat();
  closeSettingsForce();
}

// =============================================
//  AUTENTICACIÓN
// =============================================
async function doLogin() {
  const codigo = document.getElementById('input-codigo').value.trim();
  const password = document.getElementById('input-password').value.trim();
  const btn = document.getElementById('btn-login');
  const errEl = document.getElementById('login-error');

  if (!codigo || !password) {
    errEl.textContent = 'Por favor completa todos los campos.';
    errEl.style.display = 'block';
    return;
  }

  btn.disabled = true;
  errEl.style.display = 'none';

  try {
    const resp = await fetch(`${API_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Bypass-Tunnel-Reminder': 'true' },
      body: JSON.stringify({ codigo, password })
    });
    const data = await resp.json();

    if (!resp.ok) {
      errEl.textContent = data.detail || 'Credenciales inválidas.';
      errEl.style.display = 'block';
      return;
    }

    session = { token: data.token, nombre: data.nombre, rol: data.rol, codigo: data.codigo, idioma: data.idioma };
    currentLang = data.idioma || 'es';

    // ── Si es admin, guardar token y redirigir al panel de administración ──
    if (data.rol === 'admin') {
      localStorage.setItem('utpbot_token', data.token);
      localStorage.setItem('utpbot_rol', 'admin');
      localStorage.setItem('utpbot_nombre', data.nombre);
      localStorage.setItem('utpbot_codigo', data.codigo);
      window.location.href = './admin.html';
      return;
    }

    // Cargar historial global
    historialGlobal = JSON.parse(localStorage.getItem(`utpbot_hist_v2_${codigo}`) || '[]');

    // La sesión activa actual siempre inicia vacía
    historialActual = [];
    document.getElementById('messages-area').innerHTML = '';

    initChatScreen();
    document.getElementById('screen-login').classList.remove('active');
    document.getElementById('screen-chat').classList.add('active');

    // Revisar si es usuario nuevo para lanzar el tour
    if (!localStorage.getItem(`tour_done_${codigo}`)) {
      setTimeout(() => document.getElementById('welcome-modal').classList.add('show'), 400);
    }

  } catch (err) {
    errEl.textContent = 'Error de conexión con el backend de Inteligencia Artificial.';
    errEl.style.display = 'block';
  } finally {
    btn.disabled = false;
  }
}

// Cerrar sesión
function doLogout() {
  saveCurrentSessionToHistory();
  session = null;
  historialActual = [];
  document.getElementById('screen-login').classList.add('active');
  document.getElementById('screen-chat').classList.remove('active');
  document.getElementById('input-codigo').value = '';
  document.getElementById('input-password').value = '';
}

// =============================================
//  INICIALIZAR CHAT Y SIDEBAR
// =============================================
function initChatScreen() {
  document.getElementById('sidebar-name').textContent = session.nombre;

  // Reflejar idiomas base
  setLang(currentLang);

  updateHistoryTab();

  // Mensaje inicial fijo (no se guarda en la bd de historiales)
  let bienvenidaText = '';
  if (currentLang === 'es') {
    bienvenidaText = session.rol === 'estudiante'
      ? `**Saludos cordiales, ${session.nombre}.**\nSoy el motor de inteligencia artificial académica de la UTP. Mi base de conocimientos está conectada a sus registros. ¿Qué información requiere consultar hoy?`
      : `**Estimado/a Docente ${session.nombre},**\nEl sistema de asistencia de IA académica está en línea y conectado a sus registros. ¿Qué información de sus secciones requiere analizar?`;
  } else {
    bienvenidaText = session.rol === 'estudiante'
      ? `**Warm greetings, ${session.nombre}.**\nI am the UTP academic artificial intelligence engine. My knowledge base is connected to your records. What information do you need to consult today?`
      : `**Dear Faculty Member ${session.nombre},**\nThe academic AI assistance system is online and connected to your records. What section information do you need to analyze?`;
  }

  appendBotMsg(bienvenidaText, false);
}

// Cambia entre "Consultas Ágiles" y "Historial"
function switchTab(tab) {
  document.querySelectorAll('.sidebar-tab').forEach(el => el.classList.remove('active'));
  document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
  document.getElementById(`tab-${tab}`).classList.add('active');
  document.getElementById(`content-${tab}`).classList.add('active');
}

// Genera botones de consultas sugeridas dinámicamente según el rol
function buildQuickButtons() {
  const container = document.getElementById('quick-buttons');
  const keys = session.rol === 'estudiante'
    ? ['q.horario', 'q.examen', 'q.notas', 'q.trabajos', 'q.asistencia']
    : ['q.d.horario', 'q.d.alumnos', 'q.d.asistencia', 'q.d.notas'];

  container.innerHTML = keys.map(k => {
    const data = t(k);
    return `<button class="quick-btn" onclick="quickSend('${data.text}')"><i class="ph ${data.icon}"></i> ${data.text}</button>`;
  }).join('');
}

// Actualiza lista visual del panel de Historial
function updateHistoryTab() {
  const container = document.getElementById('history-list');
  if (historialGlobal.length === 0) {
    container.innerHTML = '<div style="font-size:12px; color:var(--text-2); text-align:center; padding:20px 0;">No hay sesiones guardadas.</div>';
    return;
  }

  // Ordenar del más reciente al más antiguo
  const recientes = [...historialGlobal].reverse();
  container.innerHTML = recientes.map((h, i) => {
    const preview = h.mensajes.filter(m => m.rol === 'user')[0]?.contenido || 'Sesión de consulta';
    const date = new Date(h.fecha).toLocaleDateString('es-PE', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    return `
      <div class="history-item" onclick="loadOldSession(${historialGlobal.length - 1 - i})" style="display:flex; align-items:center; justify-content:space-between">
        <div style="overflow:hidden">
          <div class="history-title"><i class="ph ph-chat-teardrop-text"></i> ${escapeHtml(preview)}</div>
          <div class="history-date">${date}</div>
        </div>
        <button class="icon-btn" onclick="deleteHistoryItem(event, '${h.id}')" style="background:none; border:none; color:var(--text-2); cursor:pointer;"><i class="ph ph-trash"></i></button>
      </div>
    `;
  }).join('');
}

function deleteHistoryItem(event, id) {
  event.stopPropagation(); // Evita que se dispare loadHistorySession
  historialGlobal = historialGlobal.filter(s => s.id !== id);
  localStorage.setItem(`utpbot_hist_v2_${session.codigo}`, JSON.stringify(historialGlobal));

  if (currentSessionId === id || !currentSessionId) {
    startNewChat();
  }
  updateHistoryTab();
}

// Cargar una conversación antigua clickeada
function loadOldSession(index) {
  const data = historialGlobal[index];
  historialActual = [...data.mensajes];

  const area = document.getElementById('messages-area');
  area.innerHTML = '';

  historialActual.forEach(m => {
    if (m.rol === 'user') appendUserMsg(m.contenido, false);
    else appendBotMsg(m.contenido, false);
  });

  if (window.innerWidth <= 768) toggleSidebar();
}

// =============================================
//  GESTIÓN DE MENSAJES Y CHAT
// =============================================

// Muestra el mensaje del usuario y lo guarda onSession
function appendUserMsg(text, save = true) {
  const area = document.getElementById('messages-area');
  const time = new Date().toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
  const div = document.createElement('div');
  div.className = 'msg-row user';

  let contentHtml = escapeHtml(text);
  if (currentFileName && save) {
    contentHtml = `<div style="font-size:11px; font-weight:600; margin-bottom:4px; opacity:0.8"><i class="ph ph-file-text"></i> ${currentFileName} adjunto</div>` + contentHtml;
  }

  div.innerHTML = `
    <div class="user-icon msg-icon"><i class="ph ph-user"></i></div>
    <div class="bubble-wrap">
      <div class="bubble">${contentHtml}</div>
      <div class="msg-time">${time}</div>
    </div>`;
  area.appendChild(div);

  if (save) {
    historialActual.push({ rol: 'user', contenido: text });
    syncCurrentSession();
  }
  scrollBottom();
}

// Muestra la burbuja del Bot usando formato HTML de Markdown
function appendBotMsg(text, save = true) {
  const area = document.getElementById('messages-area');
  const time = new Date().toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
  const div = document.createElement('div');
  div.className = 'msg-row bot';
  div.innerHTML = `
    <div class="bot-icon msg-icon"><i class="ph ph-robot"></i></div>
    <div class="bubble-wrap">
      <div class="bubble">${markdownToHtml(text)}</div>
      <div class="msg-time">${time}</div>
    </div>`;
  area.appendChild(div);

  if (save) {
    historialActual.push({ rol: 'assistant', contenido: text });
    syncCurrentSession();
  }
  scrollBottom();
}

// Conversor básico y limpio de Markdown a HTML
function markdownToHtml(text) {
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>')
    .replace(/^/, '<p>').replace(/$/, '</p>')
    .replace(/<p><\/p>/g, '');
}

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function scrollBottom() {
  const area = document.getElementById('messages-area');
  setTimeout(() => area.scrollTop = area.scrollHeight, 50);
}

// Guarda la sesión activa en el historial global y localStorage
function syncCurrentSession() {
  if (historialActual.length === 0) return;
  const code = session.codigo;
  let stored = JSON.parse(localStorage.getItem(`utpbot_hist_v2_${code}`) || '[]');

  if (!window.currentSessionId) window.currentSessionId = new Date().toISOString();

  const existingIndex = stored.findIndex(s => s.id === window.currentSessionId);
  if (existingIndex >= 0) {
    stored[existingIndex].mensajes = historialActual;
  } else {
    stored.push({
      id: window.currentSessionId,
      fecha: new Date().toISOString(),
      mensajes: historialActual
    });
  }

  localStorage.setItem(`utpbot_hist_v2_${code}`, JSON.stringify(stored));
  historialGlobal = stored;
  updateHistoryTab();
}

// Limpia el SessionId para preparar una nueva sesión
function saveCurrentSessionToHistory() {
  window.currentSessionId = null;
}

// =============================================
//  COMUNICACIÓN HTTP (API)
// =============================================
async function sendMessage() {
  const input = document.getElementById('chat-input');
  const msg = input.value.trim();
  if (!msg && !currentFileName) return;
  if (!session) return;

  const msgToSend = msg || "Revisa el documento adjunto por favor.";
  input.value = '';
  input.style.height = 'auto';

  // Guardamos temporalmente para el HTTP y limpiamos UI
  let reqFileName = currentFileName;
  let reqFileMime = currentFileMime;
  let reqFileData = currentFileData;

  appendUserMsg(msgToSend);
  clearFile();

  const typing = document.getElementById('typing');
  typing.classList.add('show');
  document.getElementById('btn-send').disabled = true;

  try {
    const resp = await fetch(`${API_URL}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${session.token}`,
        'Bypass-Tunnel-Reminder': 'true'
      },
      body: JSON.stringify({
        codigo_usuario: session.codigo, rol: session.rol, mensaje: msgToSend,
        idioma_preferido: session.idioma,
        historial: historialActual.slice(-8),
        file_name: reqFileName,
        file_mime: reqFileMime,
        file_data: reqFileData
      })
    });
    const data = await resp.json();
    typing.classList.remove('show');

    if (!resp.ok) {
      appendBotMsg(`Error de sistema: ${data.detail || 'Fallo en la comunicación.'}`);
    } else {
      appendBotMsg(data.respuesta);
    }

  } catch (err) {
    typing.classList.remove('show');
    appendBotMsg('Anomalía de red. Verifica la conexión con el clúster de IA.');
  } finally {
    document.getElementById('btn-send').disabled = false;
    scrollBottom();
  }
}

// Enviar un mensaje rápido desde los botones
function quickSend(texto) {
  document.getElementById('chat-input').value = texto;
  sendMessage();
  if (window.innerWidth <= 768) toggleSidebar();
}

function handleEnter(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
}

function autoResize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 160) + 'px';
}

function toggleSidebar() {
  document.getElementById('sidebar').classList.toggle('open');
  const overlay = document.getElementById('mobile-overlay');
  overlay.style.display = overlay.style.display === 'block' ? 'none' : 'block';
}

// =============================================
//  ONBOARDING TOUR INTERACTIVO
// =============================================
const tourSteps = [
  {
    target: 'sidebar',
    title: '<i class="ph ph-layout"></i> Panel de Control',
    text: 'En el panel lateral dispones de herramientas base. Alterna entre realizar consultas ágiles y revisar de manera estructurada tu historial de sesiones pasadas.',
    position: 'right'
  },
  {
    target: 'chat-input-wrapper',
    title: '<i class="ph ph-terminal-window"></i> Interfaz de Procesamiento',
    text: 'Esta es el área principal. Redacta solicitudes complejas de lógica natural y la Inteligencia Artificial procesará e indexará la información académica.',
    position: 'top'
  },
  {
    target: 'btn-dark',
    title: '<i class="ph ph-moon"></i> Control de Entorno Visual',
    text: 'Alterna entre temas oscuros y claros para adaptar la interfaz a tu preferencia sin afectar tus sesiones.',
    position: 'bottom-left'
  }
];

let currentStep = 0;

function closeWelcome() {
  document.getElementById('welcome-modal').classList.remove('show');
  if (session) localStorage.setItem(`tour_done_${session.codigo}`, 'true');
}

function startTour() {
  closeWelcome();
  currentStep = 0;
  if (window.innerWidth <= 768) {
    document.getElementById('sidebar').classList.add('open');
  }
  document.getElementById('tour-overlay').classList.add('show');
  renderTourStep();
}

function endTour() {
  document.getElementById('tour-overlay').classList.remove('show');
  document.getElementById('tour-box').classList.remove('active');
  document.querySelectorAll('.tour-highlight').forEach(el => el.classList.remove('tour-highlight'));
  if (window.innerWidth <= 768) {
    document.getElementById('sidebar').classList.remove('open');
  }
}

function renderTourStep() {
  document.querySelectorAll('.tour-highlight').forEach(el => el.classList.remove('tour-highlight'));

  if (currentStep >= tourSteps.length) {
    endTour();
    return;
  }

  const step = tourSteps[currentStep];
  const targetEl = document.getElementById(step.target);
  if (!targetEl) { nextTourStep(); return; }

  targetEl.classList.add('tour-highlight');

  // Posicionar caja
  const rect = targetEl.getBoundingClientRect();
  const box = document.getElementById('tour-box');

  document.getElementById('tour-title').innerHTML = step.title;
  document.getElementById('tour-text').textContent = step.text;

  // Render dots
  document.getElementById('tour-dots').innerHTML = tourSteps.map((_, i) =>
    `<div class="tour-dot ${i === currentStep ? 'active' : ''}"></div>`
  ).join('');

  document.getElementById('tour-next').textContent = currentStep === tourSteps.length - 1 ? 'Finalizar' : 'Siguiente';

  box.classList.add('active');

  // Calcular coordenadas
  let top, left;
  const padding = 16;
  if (window.innerWidth <= 768) {
    // Modo móvil centrado-inferior
    top = window.innerHeight - 240;
    left = (window.innerWidth - 320) / 2;
  } else {
    if (step.position === 'right') {
      top = rect.top + (rect.height / 2) - 100;
      left = rect.right + padding;
    } else if (step.position === 'top') {
      top = rect.top - 200;
      left = rect.left + (rect.width / 2) - 160;
    } else if (step.position === 'bottom-left') {
      top = rect.bottom + padding;
      left = rect.left - 320 + rect.width;
    }
  }

  // Prevenir desbordamientos
  top = Math.max(10, Math.min(top, window.innerHeight - 200));
  left = Math.max(10, Math.min(left, window.innerWidth - 330));

  box.style.top = `${top}px`;
  box.style.left = `${left}px`;
}

function nextTourStep() {
  currentStep++;
  renderTourStep();
}

// =============================================
//  MODO VOZ EN VIVO (Web Speech API)
// =============================================
let recognition = null;
let isVoiceModeActive = false;
let isBotSpeaking = false;
let isProcessingVoice = false; // Lock anti-duplicados
let silenceTimer = null; // Temporizador de silencio
let synthesis = window.speechSynthesis;

function _crearRecognition() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) return null;

  const rec = new SpeechRecognition();
  rec.lang = currentLang === 'es' ? 'es-ES' : 'en-US';
  rec.continuous = true;      // Continuar escuchando para permitir pausas
  rec.interimResults = true;

  // Almacenar el texto acumulado
  let textoAcumulado = '';

  rec.onresult = (event) => {
    // Si ya estamos procesando o el bot habla, ignorar
    if (isBotSpeaking || isProcessingVoice) return;

    // Reiniciar el temporizador en cada palabra nueva
    clearTimeout(silenceTimer);

    let fullTranscript = '';
    for (let i = 0; i < event.results.length; ++i) {
      fullTranscript += event.results[i][0].transcript;
    }

    const displayEl = document.getElementById('voice-transcript');
    const trimmedText = fullTranscript.trim();

    if (trimmedText) {
      displayEl.textContent = trimmedText;

      // Si hay un texto válido, esperamos a que el usuario deje de hablar
      silenceTimer = setTimeout(() => {
        isProcessingVoice = true;  // LOCK: bloquear nuevas capturas
        rec.stop();                // Detener escucha manualmente
        document.getElementById('chat-input').value = trimmedText;
        sendMessageFromVoice();
      }, 1000); // 1.0 segundos de pausa para mayor velocidad y fluidez
    }
  };

  rec.onerror = (event) => {
    console.error('Speech recognition error:', event.error);
    const transcriptEl = document.getElementById('voice-transcript');

    if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
      alert(currentLang === 'es'
        ? 'Permiso de micrófono denegado. Actívalo en la configuración del navegador (clic en el candado de la barra de direcciones).'
        : 'Microphone permission denied. Enable it in your browser settings (click the lock icon in the address bar).');
      stopVoiceMode();
    } else if (event.error === 'network') {
      if (transcriptEl) transcriptEl.textContent = currentLang === 'es'
        ? 'Error de red. Reintentando...' : 'Network error. Retrying...';
      // Chrome usa servidores de Google para procesar voz, necesita conexión
    } else if (event.error === 'no-speech') {
      if (transcriptEl) transcriptEl.textContent = currentLang === 'es'
        ? 'No se detectó voz. Habla cerca del micrófono...' : 'No speech detected. Speak closer to the mic...';
    } else if (event.error === 'aborted') {
      // Aborted es normal cuando nosotros detenemos manualmente, ignorar
    }
  };

  rec.onend = () => {
    // Solo reiniciar si el modo voz sigue activo Y no estamos bloqueados
    if (isVoiceModeActive && !isBotSpeaking && !isProcessingVoice) {
      _startListening();
    }
  };

  return rec;
}

let _voiceRetryCount = 0;
const MAX_VOICE_RETRIES = 3;

function _startListening() {
  if (!isVoiceModeActive || isBotSpeaking || isProcessingVoice) return;
  if (!recognition) return;
  try {
    recognition.start();
    _voiceRetryCount = 0; // Reset en inicio exitoso
  } catch (e) {
    console.warn('SpeechRecognition.start() error:', e.message);
    // Si ya estaba corriendo, reintentar con delay
    if (_voiceRetryCount < MAX_VOICE_RETRIES) {
      _voiceRetryCount++;
      setTimeout(_startListening, 300);
    }
  }
}

async function startVoiceMode() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    alert('Tu navegador no soporta el reconocimiento de voz. Usa Google Chrome o Microsoft Edge.');
    return;
  }

  // Chrome requiere solicitar permisos de micrófono EXPLÍCITAMENTE
  // antes de poder usar SpeechRecognition. Edge lo maneja internamente.
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    // Liberar el stream inmediatamente, solo necesitábamos el permiso
    stream.getTracks().forEach(track => track.stop());
  } catch (micError) {
    console.error('Microphone permission error:', micError);
    alert(currentLang === 'es'
      ? 'No se pudo acceder al micrófono. Verifica que el permiso esté habilitado en la configuración de tu navegador (clic en el candado de la barra de direcciones).'
      : 'Could not access the microphone. Check that the permission is enabled in your browser settings (click the lock icon in the address bar).');
    return;
  }

  // Asegurarse de cerrar cualquier sesión previa
  if (recognition) {
    try { recognition.abort(); } catch (e) { }
    recognition = null;
  }
  synthesis.cancel();

  recognition = _crearRecognition();
  isVoiceModeActive = true;
  isBotSpeaking = false;
  isProcessingVoice = false;
  _voiceRetryCount = 0;

  // UI
  document.getElementById('voice-modal').classList.add('show');
  document.getElementById('btn-voice-toggle').classList.add('active');
  setVoiceUIState('listening');
  document.getElementById('voice-transcript').textContent = currentLang === 'es'
    ? 'Escuchando tu consulta...' : 'Listening for your query...';

  setTimeout(_startListening, 200);
}

function stopVoiceMode() {
  isVoiceModeActive = false;
  isBotSpeaking = false;
  isProcessingVoice = false;
  clearTimeout(silenceTimer);

  synthesis.cancel();

  if (recognition) {
    try { recognition.abort(); } catch (e) { }
    recognition = null;
  }

  document.getElementById('voice-modal').classList.remove('show');
  document.getElementById('btn-voice-toggle').classList.remove('active');
}

function setVoiceUIState(state) {
  const statusEl = document.querySelector('.voice-status');
  const textEl = document.getElementById('voice-state-text');
  const wavesEl = document.getElementById('voice-waves');
  if (!statusEl) return;

  statusEl.classList.remove('listening', 'speaking');

  if (state === 'listening') {
    statusEl.classList.add('listening');
    textEl.textContent = currentLang === 'es' ? 'Escuchando...' : 'Listening...';
    wavesEl.classList.add('active');
  } else if (state === 'speaking') {
    statusEl.classList.add('speaking');
    textEl.textContent = currentLang === 'es' ? 'Respondiendo...' : 'Speaking...';
    wavesEl.classList.add('active');
  } else if (state === 'processing') {
    statusEl.classList.add('listening');
    textEl.textContent = currentLang === 'es' ? 'Procesando...' : 'Processing...';
    wavesEl.classList.remove('active');
  }
}

// Versión de sendMessage para el modo voz (sin archivos adjuntos)
async function sendMessageFromVoice() {
  const input = document.getElementById('chat-input');
  const msg = input.value.trim();
  if (!msg || !session) {
    isProcessingVoice = false;
    return;
  }

  input.value = '';
  appendUserMsg(msg);

  // Detener el reconocimiento mientras procesamos
  isBotSpeaking = true;
  if (recognition) {
    try { recognition.abort(); } catch (e) { }
  }

  setVoiceUIState('processing');

  try {
    const resp = await fetch(`${API_URL}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${session.token}`,
        'Bypass-Tunnel-Reminder': 'true'
      },
      body: JSON.stringify({
        codigo_usuario: session.codigo,
        rol: session.rol,
        mensaje: msg,
        idioma_preferido: session.idioma,
        historial: historialActual.slice(-8)
      })
    });

    const data = await resp.json();

    if (resp.ok) {
      appendBotMsg(data.respuesta);
      if (isVoiceModeActive) {
        speakResponse(data.respuesta);
      }
    } else {
      // Error de API: volver a escuchar
      _reanudarEscucha();
    }
  } catch (err) {
    console.error('Voice send error:', err);
    _reanudarEscucha();
  }
}

function _reanudarEscucha() {
  if (!isVoiceModeActive) return;

  // Forzar reseteo de estados
  isBotSpeaking = false;
  isProcessingVoice = false;

  setVoiceUIState('listening');
  const transcriptEl = document.getElementById('voice-transcript');
  if (transcriptEl) {
    transcriptEl.textContent = currentLang === 'es' ? 'Escuchando...' : 'Listening...';
  }

  // Crear nuevo recognition si es necesario
  if (recognition) {
    try { recognition.abort(); } catch (e) { }
  }
  recognition = _crearRecognition();
  setTimeout(_startListening, 150);
}

// Sintetizar voz a partir de texto (limpia markdown primero)
function speakResponse(markdownText) {
  if (!isVoiceModeActive) return;

  // Cancelar cualquier síntesis pendiente ANTES de hablar
  synthesis.cancel();

  // Limpiar markdown
  let plainText = markdownText
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/\*(.*?)\*/g, '$1')
    .replace(/#{1,6}\s?/g, '')
    .replace(/```[\s\S]*?```/g, '')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/^[-•*]\s+/gm, '')
    .replace(/\n{2,}/g, '. ')
    .replace(/\n/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim();

  // Limitar a 400 caracteres para mayor fluidez en voz
  if (plainText.length > 400) {
    plainText = plainText.substring(0, 397) + '...';
  }

  const utterance = new SpeechSynthesisUtterance(plainText);
  utterance.lang = currentLang === 'es' ? 'es-ES' : 'en-US';
  utterance.rate = 1.0;
  utterance.pitch = 1.05;

  // Evitar acentos argentinos (es-AR) y preferir Perú (es-PE), México (es-MX) o neutro
  const availableVoices = synthesis.getVoices().filter(v =>
    v.lang.startsWith(currentLang === 'es' ? 'es' : 'en') && !v.lang.includes('AR')
  );

  // 1. Buscar voces de Perú o México explícitamente que sean Premium/Online
  let bestVoice = availableVoices.find(v =>
    (v.lang.includes('PE') || v.lang.includes('MX')) &&
    (v.name.includes('Natural') || v.name.includes('Online') || v.name.includes('Premium'))
  );

  // 2. Buscar cualquier voz Premium, Natural u Online (sin acento argentino)
  if (!bestVoice) {
    bestVoice = availableVoices.find(v =>
      v.name.includes('Natural') || v.name.includes('Online') || v.name.includes('Premium')
    );
  }

  // 3. Fallback a la voz de Google
  if (!bestVoice) {
    bestVoice = availableVoices.find(v => v.name.includes('Google'));
  }

  // 4. Fallback a una voz femenina neutra
  if (!bestVoice) {
    bestVoice = availableVoices.find(v => v.name.includes('Dalia') || v.name.includes('Sabina'));
  }

  // 4. Fallback final a la primera disponible
  if (!bestVoice && availableVoices.length > 0) {
    bestVoice = availableVoices[0];
  }

  if (bestVoice) {
    utterance.voice = bestVoice;
  }

  utterance.onstart = () => {
    setVoiceUIState('speaking');
    document.getElementById('voice-transcript').textContent =
      plainText.substring(0, 80) + (plainText.length > 80 ? '...' : '');
  };

  utterance.onend = () => {
    clearTimeout(safetyTimer);
    _reanudarEscucha();
  };

  utterance.onerror = (err) => {
    console.error('Synthesis error:', err);
    clearTimeout(safetyTimer);
    _reanudarEscucha();
  };

  // Workaround para bug conocido de Chrome que a veces no dispara onend
  const duracionEstimada = Math.max(plainText.length * 80, 2000);
  const safetyTimer = setTimeout(() => {
    if (isBotSpeaking && isVoiceModeActive) {
      console.warn('Synthesis safety timer fired');
      synthesis.cancel();
      _reanudarEscucha();
    }
  }, duracionEstimada + 2000);

  synthesis.speak(utterance);
}

// Cargar voces disponibles al inicio (algunas las carga asíncronamente)
if (typeof speechSynthesis !== 'undefined' && speechSynthesis.onvoiceschanged !== undefined) {
  speechSynthesis.onvoiceschanged = () => synthesis.getVoices();
}


