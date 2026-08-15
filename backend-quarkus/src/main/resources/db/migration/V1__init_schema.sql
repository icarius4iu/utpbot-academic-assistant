-- ============================================================================
-- UTPBot - Esquema inicial PostgreSQL (Supabase)
-- Migra las 12 hojas de Google Sheets a 13 tablas normalizadas.
-- Ver: /home/codespace/.claude/plans/bright-doodling-twilight.md
-- ============================================================================

CREATE TABLE estudiantes (
    id                  BIGSERIAL PRIMARY KEY,
    codigo              VARCHAR(20)  NOT NULL UNIQUE,
    nombre              VARCHAR(200) NOT NULL,
    carrera             VARCHAR(150) NOT NULL,
    ciclo               VARCHAR(5)   NOT NULL,
    idioma_preferido    VARCHAR(5)   NOT NULL DEFAULT 'es',
    password_hash       VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE docentes (
    id                  BIGSERIAL PRIMARY KEY,
    codigo              VARCHAR(20)  NOT NULL UNIQUE,
    nombre              VARCHAR(200) NOT NULL,
    departamento        VARCHAR(150) NOT NULL,
    cursos_asignados    TEXT,
    idioma_preferido    VARCHAR(5)   NOT NULL DEFAULT 'es',
    password_hash       VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE horarios (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    curso           VARCHAR(150) NOT NULL,
    dia             VARCHAR(15)  NOT NULL,
    hora_inicio     TIME NOT NULL,
    hora_fin        TIME NOT NULL,
    aula            VARCHAR(20)  NOT NULL,
    docente_nombre  VARCHAR(200) NOT NULL  -- texto libre, no FK: ver nota en el plan (§ETL)
);
CREATE INDEX idx_horarios_estudiante ON horarios(estudiante_id);

CREATE TABLE notas (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    curso           VARCHAR(150) NOT NULL,
    parcial         NUMERIC(4,2),
    final           NUMERIC(4,2),
    promedio        NUMERIC(4,2)
);
CREATE INDEX idx_notas_estudiante ON notas(estudiante_id);

CREATE TABLE cursos (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    nombre_curso    VARCHAR(150) NOT NULL,
    creditos        SMALLINT NOT NULL,
    estado          VARCHAR(30) NOT NULL
);
CREATE INDEX idx_cursos_estudiante ON cursos(estudiante_id);

CREATE TABLE examenes (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    curso           VARCHAR(150) NOT NULL,
    tipo            VARCHAR(30) NOT NULL,
    fecha           DATE NOT NULL,
    hora            TIME,
    aula            VARCHAR(20)
);
CREATE INDEX idx_examenes_estudiante ON examenes(estudiante_id);

CREATE TABLE asistencia (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    curso           VARCHAR(150) NOT NULL,
    fecha           DATE NOT NULL,
    estado          VARCHAR(20) NOT NULL
);
CREATE INDEX idx_asistencia_estudiante ON asistencia(estudiante_id);

CREATE TABLE avances_trabajo (
    id              BIGSERIAL PRIMARY KEY,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    curso           VARCHAR(150) NOT NULL,
    entregable      VARCHAR(150) NOT NULL,
    fecha_entrega   DATE,
    estado          VARCHAR(20) NOT NULL,
    nota            NUMERIC(4,2)
);
CREATE INDEX idx_avances_estudiante ON avances_trabajo(estudiante_id);

CREATE TABLE proyectos_finales (
    id                  BIGSERIAL PRIMARY KEY,
    estudiante_id       BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    curso               VARCHAR(150) NOT NULL,
    titulo              VARCHAR(250) NOT NULL,
    grupo               VARCHAR(30),
    fecha_sustentacion  DATE,
    nota                NUMERIC(4,2)
);
CREATE INDEX idx_proyectos_estudiante ON proyectos_finales(estudiante_id);

CREATE TABLE eventos_calendario (
    id           BIGSERIAL PRIMARY KEY,
    fecha        DATE NOT NULL,
    evento       VARCHAR(200) NOT NULL,
    descripcion  TEXT,
    aplica_a     VARCHAR(15) NOT NULL CHECK (aplica_a IN ('todos', 'estudiantes', 'docentes'))
);
CREATE INDEX idx_eventos_aplica_a ON eventos_calendario(aplica_a);

CREATE TABLE secciones_docente (
    id          BIGSERIAL PRIMARY KEY,
    docente_id  BIGINT NOT NULL REFERENCES docentes(id) ON DELETE CASCADE,
    curso       VARCHAR(150) NOT NULL,
    seccion     VARCHAR(10) NOT NULL,
    horario     VARCHAR(150)
);
CREATE INDEX idx_secciones_docente ON secciones_docente(docente_id);

-- Normaliza Secciones_Docente.lista_estudiantes (string JSON en Sheets) a filas reales.
CREATE TABLE secciones_docente_estudiantes (
    seccion_id      BIGINT NOT NULL REFERENCES secciones_docente(id) ON DELETE CASCADE,
    estudiante_id   BIGINT NOT NULL REFERENCES estudiantes(id) ON DELETE CASCADE,
    PRIMARY KEY (seccion_id, estudiante_id)
);

-- Log de consultas / analytics — reemplaza la hoja FAQ_Log.
-- Arranca vacía: por decisión del usuario, el historial de FAQ_Log NO se migra.
CREATE TABLE consulta_log (
    id              BIGSERIAL PRIMARY KEY,
    fecha           TIMESTAMPTZ NOT NULL DEFAULT now(),
    codigo_usuario  VARCHAR(20) NOT NULL,
    rol             VARCHAR(15) NOT NULL,
    pregunta        TEXT NOT NULL,
    categoria       VARCHAR(30) NOT NULL DEFAULT 'general'
);
CREATE INDEX idx_consulta_log_fecha     ON consulta_log(fecha DESC);
CREATE INDEX idx_consulta_log_categoria ON consulta_log(categoria);
CREATE INDEX idx_consulta_log_rol       ON consulta_log(rol);
-- Nota: no se crea un índice de expresión (fecha::date) — el cast timestamptz→date
-- depende de la zona horaria de sesión, así que Postgres lo rechaza como no-IMMUTABLE
-- en una definición de índice. Las consultas "por día" (Fase 3, AnalyticsService)
-- deben filtrar con rangos (fecha >= :inicio AND fecha < :fin), que sí aprovechan
-- idx_consulta_log_fecha normalmente.
