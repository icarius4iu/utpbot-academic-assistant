-- ============================================================================
-- Módulo de estudio personalizado
--
-- Historias de usuario que soporta:
--  1. Subir sílabo → ruta de estudio con temas ordenados
--  2. Subir materiales (PDF/PPT/DOCX) → cuestionarios y resúmenes contextuados
--  3. Meta diaria en minutos + racha de estudio
--
-- Nota de diseño: se guarda el TEXTO EXTRAÍDO de cada archivo, no el binario.
-- Alcanza para generar rutas, resúmenes y cuestionarios, y evita cargar la base
-- con archivos pesados. Si más adelante hace falta re-descargar el original,
-- habría que sumar almacenamiento externo (Supabase/Firebase Storage).
-- ============================================================================

-- ─── Materiales subidos (sílabos y material de apoyo) ──────────────────────
CREATE TABLE material_estudio (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    codigo_curso    VARCHAR(20),
    tipo            VARCHAR(15) NOT NULL CHECK (tipo IN ('SILABO', 'MATERIAL')),
    nombre_archivo  VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(120),
    texto_extraido  TEXT NOT NULL,
    caracteres      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_material_estudiante ON material_estudio(estudiante_id, created_at DESC);

-- ─── Ruta de estudio generada desde un sílabo ──────────────────────────────
CREATE TABLE ruta_estudio (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    material_id     BIGINT REFERENCES material_estudio(id) ON DELETE SET NULL,
    curso           VARCHAR(150) NOT NULL,
    titulo          VARCHAR(200) NOT NULL,
    descripcion     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ruta_estudiante ON ruta_estudio(estudiante_id, created_at DESC);

CREATE TABLE tema_ruta (
    id              BIGSERIAL PRIMARY KEY,
    ruta_id         BIGINT NOT NULL REFERENCES ruta_estudio(id) ON DELETE CASCADE,
    orden           SMALLINT NOT NULL,
    titulo          VARCHAR(200) NOT NULL,
    descripcion     TEXT,
    horas_estimadas NUMERIC(4,1),
    completado      BOOLEAN NOT NULL DEFAULT FALSE,
    completado_at   TIMESTAMPTZ
);
CREATE INDEX idx_tema_ruta ON tema_ruta(ruta_id, orden);

-- ─── Cuestionarios generados desde materiales ──────────────────────────────
CREATE TABLE cuestionario (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    material_id     BIGINT REFERENCES material_estudio(id) ON DELETE SET NULL,
    titulo          VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cuestionario_estudiante ON cuestionario(estudiante_id, created_at DESC);

CREATE TABLE pregunta_cuestionario (
    id                  BIGSERIAL PRIMARY KEY,
    cuestionario_id     BIGINT NOT NULL REFERENCES cuestionario(id) ON DELETE CASCADE,
    orden               SMALLINT NOT NULL,
    enunciado           TEXT NOT NULL,
    -- Opciones separadas por salto de línea (4 típicamente). Se evita jsonb para
    -- mantener el mapeo simple con Panache y no depender de un dialecto específico.
    opciones            TEXT NOT NULL,
    indice_correcto     SMALLINT NOT NULL,
    explicacion         TEXT
);
CREATE INDEX idx_pregunta_cuestionario ON pregunta_cuestionario(cuestionario_id, orden);

-- ─── Resúmenes generados ───────────────────────────────────────────────────
CREATE TABLE resumen_material (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    material_id     BIGINT REFERENCES material_estudio(id) ON DELETE SET NULL,
    contenido       TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_resumen_estudiante ON resumen_material(estudiante_id, created_at DESC);

-- ─── Meta diaria y sesiones de estudio (racha) ─────────────────────────────
CREATE TABLE meta_estudio (
    id                  BIGSERIAL PRIMARY KEY,
    estudiante_id       BIGINT NOT NULL UNIQUE REFERENCES estudiantes(id) ON DELETE CASCADE,
    minutos_diarios     SMALLINT NOT NULL DEFAULT 30,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sesion_estudio (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    -- fecha local de Lima (no UTC): la racha se cuenta en días del calendario del
    -- alumno, así que la conversión de zona horaria se hace al registrar, no al leer.
    fecha           DATE NOT NULL,
    minutos         SMALLINT NOT NULL CHECK (minutos > 0),
    tema_id         BIGINT REFERENCES tema_ruta(id) ON DELETE SET NULL,
    nota            VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sesion_estudiante_fecha ON sesion_estudio(estudiante_id, fecha DESC);
