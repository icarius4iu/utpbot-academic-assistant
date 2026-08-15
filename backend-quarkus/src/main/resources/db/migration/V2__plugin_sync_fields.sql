-- ============================================================================
-- UTPBot - Soporte para la extensión de navegador "UTPBot Sync"
-- (autoexportación de datos desde el Portal del Estudiante UTP).
--
-- Ver: etl/UTPBotSync_GuiaImplementacion.md — los campos de acá salen de la API
-- real del portal (api-pao.utpxpedition.com), verificada en vivo.
-- ============================================================================

-- ─── cursos ────────────────────────────────────────────────────────────────
-- codigo_curso: el "classNumber" del portal (ej. "31088") — es el código que el
-- alumno ve en la UI y la clave natural para hacer upsert al re-sincronizar.
ALTER TABLE cursos ADD COLUMN IF NOT EXISTS codigo_curso VARCHAR(20);
ALTER TABLE cursos ADD COLUMN IF NOT EXISTS modalidad    VARCHAR(20);
ALTER TABLE cursos ADD COLUMN IF NOT EXISTS progreso     SMALLINT;
ALTER TABLE cursos ADD COLUMN IF NOT EXISTS docente      VARCHAR(200);

-- creditos era NOT NULL, pero la API del portal NO expone créditos (ver §9 de la
-- guía: "queda pendiente"). Sin relajarlo, toda sincronización desde el plugin
-- fallaría. El ETL desde Sheets sí los trae, así que el dato sigue siendo válido
-- cuando está disponible — simplemente ya no es obligatorio.
ALTER TABLE cursos ALTER COLUMN creditos DROP NOT NULL;

-- ─── horarios ──────────────────────────────────────────────────────────────
-- codigo_curso permite cruzar el bloque de horario con su curso sin depender del
-- nombre (que el portal devuelve en MAYÚSCULAS y puede variar).
ALTER TABLE horarios ADD COLUMN IF NOT EXISTS codigo_curso VARCHAR(20);
ALTER TABLE horarios ADD COLUMN IF NOT EXISTS modalidad    VARCHAR(20);

-- aula y docente_nombre eran NOT NULL. El portal devuelve classroom = null hoy
-- (el campo existe pero está sin poblar), y no todos los bloques traen docente.
ALTER TABLE horarios ALTER COLUMN aula           DROP NOT NULL;
ALTER TABLE horarios ALTER COLUMN docente_nombre DROP NOT NULL;

-- ─── Índices únicos para que el upsert sea determinístico ──────────────────
-- Sin esto, re-sincronizar duplicaría filas en vez de actualizarlas.
-- Parciales (WHERE ... IS NOT NULL) para no chocar con las filas que ya cargó el
-- ETL desde Sheets, que no tienen codigo_curso.
CREATE UNIQUE INDEX IF NOT EXISTS ux_cursos_est_codigo
  ON cursos (estudiante_id, codigo_curso)
  WHERE codigo_curso IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_horarios_est_bloque
  ON horarios (estudiante_id, codigo_curso, dia, hora_inicio)
  WHERE codigo_curso IS NOT NULL;

-- ─── estudiantes ───────────────────────────────────────────────────────────
-- El plugin puede traer el ciclo del portal (ej. "2026 - Ciclo 2 Agosto"), más
-- largo que el VARCHAR(5) original pensado para un número de ciclo ("5").
ALTER TABLE estudiantes ALTER COLUMN ciclo TYPE VARCHAR(60);
-- carrera aún no se puede obtener del portal (ver §9 de la guía) — se permite
-- null para que el plugin no la pise con un valor vacío al sincronizar.
ALTER TABLE estudiantes ALTER COLUMN carrera DROP NOT NULL;
