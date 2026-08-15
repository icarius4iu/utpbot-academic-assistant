package com.utpbot.service;

import com.utpbot.dto.sync.SincronizarRequest;
import com.utpbot.dto.sync.SincronizarResponse;
import com.utpbot.entity.Curso;
import com.utpbot.entity.Estudiante;
import com.utpbot.entity.Horario;
import com.utpbot.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Aplica al modelo de UTPBot los datos que la extensión "UTPBot Sync" leyó del Portal
 * del Estudiante. Semántica de UPSERT: re-sincronizar actualiza en vez de duplicar
 * (índices únicos parciales en V2__plugin_sync_fields.sql lo respaldan a nivel DB).
 *
 * Regla de seguridad central: el `codigo` del estudiante SIEMPRE llega desde el token
 * verificado (lo pasa EstudianteResource), nunca desde el body — así un alumno no
 * puede escribir datos en el registro de otro aunque manipule el JSON del plugin.
 */
@ApplicationScoped
public class EstudianteSyncService {

    private static final Logger LOG = Logger.getLogger(EstudianteSyncService.class);

    @Transactional
    public SincronizarResponse sincronizar(String codigoAutenticado, SincronizarRequest req) {
        Estudiante estudiante = Estudiante.findByCodigo(codigoAutenticado);
        if (estudiante == null) {
            throw ApiException.notFound(
                    "No existe un estudiante con el código " + codigoAutenticado + " en UTPBot.");
        }

        SincronizarResponse resp = new SincronizarResponse();
        resp.codigoEstudiante = codigoAutenticado;

        aplicarPerfil(estudiante, req, resp);
        if (req.cursos != null) {
            for (SincronizarRequest.CursoSync c : req.cursos) {
                aplicarCurso(estudiante, c, resp);
            }
        }
        if (req.horarios != null) {
            for (SincronizarRequest.HorarioSync h : req.horarios) {
                aplicarHorario(estudiante, h, resp);
            }
        }

        estudiante.updatedAt = OffsetDateTime.now();
        estudiante.persist();

        LOG.infof("Sync de %s: %d cursos (+%d nuevos), %d horarios (+%d nuevos)",
                codigoAutenticado, resp.cursosActualizados, resp.cursosCreados,
                resp.horariosActualizados, resp.horariosCreados);
        return resp;
    }

    // ===================== PERFIL =====================

    private void aplicarPerfil(Estudiante estudiante, SincronizarRequest req, SincronizarResponse resp) {
        if (req.perfil == null) {
            return;
        }

        // El código del portal puede diferir del de UTPBot (distintos formatos institucionales).
        // No es un error que bloquee la sync, pero conviene avisarlo.
        if (req.perfil.codigo != null && !req.perfil.codigo.isBlank()
                && !req.perfil.codigo.equalsIgnoreCase(estudiante.codigo)) {
            resp.avisos.add("El código del portal (" + req.perfil.codigo + ") no coincide con tu código en UTPBot ("
                    + estudiante.codigo + "). Se guardó bajo tu código de UTPBot.");
        }

        // Solo se pisa lo que viene con valor: si el portal todavía no expone `carrera`
        // (ver §9 de la guía), no se borra la que ya estuviera cargada por el ETL.
        if (esUtil(req.perfil.nombre)) {
            estudiante.nombre = req.perfil.nombre;
        }
        if (esUtil(req.perfil.carrera)) {
            estudiante.carrera = req.perfil.carrera;
        }
        if (esUtil(req.perfil.ciclo)) {
            estudiante.ciclo = req.perfil.ciclo;
        }
    }

    // ===================== CURSOS =====================

    private void aplicarCurso(Estudiante estudiante, SincronizarRequest.CursoSync c, SincronizarResponse resp) {
        if (!esUtil(c.codigoCurso)) {
            resp.avisos.add("Se ignoró un curso sin código: " + c.nombreCurso);
            return;
        }

        Curso curso = Curso.findByEstudianteYCodigo(estudiante.id, c.codigoCurso);
        boolean esNuevo = curso == null;
        if (esNuevo) {
            curso = new Curso();
            curso.estudiante = estudiante;
            curso.codigoCurso = c.codigoCurso;
            curso.estado = "en curso";
        }

        if (esUtil(c.nombreCurso)) {
            curso.nombreCurso = c.nombreCurso;
        }
        curso.modalidad = c.modalidad;
        curso.docente = c.docente;
        curso.progreso = c.progresoPorcentaje == null ? null : (short) (int) c.progresoPorcentaje;
        curso.persist();

        if (esNuevo) {
            resp.cursosCreados++;
        } else {
            resp.cursosActualizados++;
        }
    }

    // ===================== HORARIOS =====================

    private void aplicarHorario(Estudiante estudiante, SincronizarRequest.HorarioSync h, SincronizarResponse resp) {
        if (!esUtil(h.codigoCurso) || !esUtil(h.dia) || !esUtil(h.horaInicio) || !esUtil(h.horaFin)) {
            resp.avisos.add("Se ignoró un bloque de horario incompleto (curso " + h.codigoCurso + ", " + h.dia + ").");
            return;
        }

        LocalTime inicio = parseHora(h.horaInicio);
        LocalTime fin = parseHora(h.horaFin);
        if (inicio == null || fin == null) {
            resp.avisos.add("Se ignoró un bloque con hora inválida: " + h.horaInicio + "-" + h.horaFin);
            return;
        }

        Horario horario = Horario.findBloque(estudiante.id, h.codigoCurso, h.dia, inicio);
        boolean esNuevo = horario == null;
        if (esNuevo) {
            horario = new Horario();
            horario.estudiante = estudiante;
            horario.codigoCurso = h.codigoCurso;
            horario.dia = h.dia;
            horario.horaInicio = inicio;
        }

        horario.horaFin = fin;
        horario.aula = h.aula;
        horario.modalidad = h.modalidad;

        // `curso` (nombre) es NOT NULL en el schema y el plugin manda solo el código —
        // se resuelve contra el curso ya sincronizado; si no está, se usa el código como
        // texto para no violar la constraint.
        Curso curso = Curso.findByEstudianteYCodigo(estudiante.id, h.codigoCurso);
        horario.curso = (curso != null && esUtil(curso.nombreCurso)) ? curso.nombreCurso : h.codigoCurso;
        if (curso != null && esUtil(curso.docente)) {
            horario.docenteNombre = curso.docente;
        }

        horario.persist();

        if (esNuevo) {
            resp.horariosCreados++;
        } else {
            resp.horariosActualizados++;
        }
    }

    // ===================== UTILIDADES =====================

    private static boolean esUtil(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** Acepta "18:30" y "18:30:00"; devuelve null si no parsea (el caller lo reporta como aviso). */
    private static LocalTime parseHora(String valor) {
        try {
            String limpio = valor.strip();
            return LocalTime.parse(limpio.length() == 5 ? limpio + ":00" : limpio);
        } catch (Exception e) {
            return null;
        }
    }
}
