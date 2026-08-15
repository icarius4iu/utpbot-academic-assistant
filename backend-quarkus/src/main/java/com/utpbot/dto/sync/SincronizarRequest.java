package com.utpbot.dto.sync;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrato de entrada de POST /estudiante/sincronizar — lo produce la extensión de
 * navegador "UTPBot Sync" leyendo la API interna del Portal del Estudiante UTP.
 * Ver etl/UTPBotSync_GuiaImplementacion.md §4 para el mapeo campo por campo.
 *
 * El JSON llega en snake_case (codigo_curso, hora_inicio, ...) y Jackson lo mapea
 * solo gracias a quarkus.jackson.property-naming-strategy=SNAKE_CASE.
 */
public class SincronizarRequest {

    public Perfil perfil;
    public List<CursoSync> cursos = new ArrayList<>();
    public List<HorarioSync> horarios = new ArrayList<>();

    public static class Perfil {
        /**
         * Código institucional según el portal (ej. "u21206744"). Se recibe pero NO se
         * confía en él para decidir a quién escribir — eso sale del token verificado
         * (ver EstudianteResource). Solo se usa para avisar si no coincide.
         */
        public String codigo;
        public String nombre;
        public String carrera;
        public String ciclo;
        public String cicloCodigo;
    }

    public static class CursoSync {
        public String codigoCurso;
        public String nombreCurso;
        public String modalidad;
        public String docente;
        public Integer progresoPorcentaje;
    }

    public static class HorarioSync {
        public String codigoCurso;
        public String dia;
        public String horaInicio;
        public String horaFin;
        public String modalidad;
        public String aula;
    }
}
