package com.utpbot.service;

import com.utpbot.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Equivalente a sheets_service.py: recopilar_datos_estudiante() / recopilar_datos_docente()
 * y todos los obtener_*(). Construye el MISMO shape de JSON anidado que Python arma (mismas
 * claves, mismo anidamiento) — ese dict se vuelca luego, verbatim, dentro del prompt de
 * sistema (json.dumps(datos_usuario, ...) en prompt_builder.py), así que las claves deben
 * coincidir exactamente para que el comportamiento del asistente no cambie.
 *
 * A diferencia de Python (que reintenta Sheets y cae a datos demo si falla), aquí no hay
 * fallback demo: los datos viven en Postgres, ya migrados por el ETL — si el código no
 * existe, el mapa resultante viene vacío (equivalente al caso "estudiante is None" ⇒ {}).
 */
@ApplicationScoped
public class AcademicDataService {

    @Transactional
    public Map<String, Object> recopilarDatosEstudiante(String codigo) {
        Estudiante estudiante = Estudiante.findByCodigo(codigo);
        if (estudiante == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("info_personal", infoPersonalEstudiante(estudiante));
        datos.put("carrera", estudiante.carrera);
        datos.put("ciclo", estudiante.ciclo);
        datos.put("idioma_preferido", nvl(estudiante.idiomaPreferido, "es"));
        datos.put("horarios", horarios(estudiante));
        datos.put("notas", notas(estudiante));
        datos.put("cursos", cursos(estudiante));
        datos.put("examenes", examenes(estudiante));
        datos.put("asistencia", asistencia(estudiante));
        datos.put("avances_trabajo", avances(estudiante));
        datos.put("proyectos_finales", proyectos(estudiante));
        datos.put("calendario", calendario("estudiantes"));
        return datos;
    }

    @Transactional
    public Map<String, Object> recopilarDatosDocente(String codigo) {
        Docente docente = Docente.findByCodigo(codigo);
        if (docente == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("info_personal", infoPersonalDocente(docente));
        datos.put("departamento", docente.departamento);
        datos.put("cursos_asignados", docente.cursosAsignados);
        datos.put("idioma_preferido", nvl(docente.idiomaPreferido, "es"));
        datos.put("secciones", secciones(docente));
        datos.put("datos_estudiantes", datosEstudiantesSeccion(docente));
        datos.put("calendario", calendario("docentes"));
        return datos;
    }

    // ===================== INFO PERSONAL =====================

    private Map<String, Object> infoPersonalEstudiante(Estudiante e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", e.codigo);
        m.put("nombre", e.nombre);
        m.put("carrera", e.carrera);
        m.put("ciclo", e.ciclo);
        m.put("idioma_preferido", nvl(e.idiomaPreferido, "es"));
        return m;
    }

    private Map<String, Object> infoPersonalDocente(Docente d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", d.codigo);
        m.put("nombre", d.nombre);
        m.put("departamento", d.departamento);
        m.put("cursos_asignados", d.cursosAsignados);
        return m;
    }

    // ===================== DATOS ACADÉMICOS DEL ESTUDIANTE =====================

    private List<Map<String, Object>> horarios(Estudiante e) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Horario h : Horario.findByEstudiante(e.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo_estudiante", e.codigo);
            m.put("curso", h.curso);
            m.put("dia", h.dia);
            m.put("hora_inicio", h.horaInicio.toString());
            m.put("hora_fin", h.horaFin.toString());
            m.put("aula", h.aula);
            m.put("docente", h.docenteNombre);
            m.put("modalidad", h.modalidad);
            lista.add(m);
        }
        return lista;
    }

    private List<Map<String, Object>> notas(Estudiante e) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Nota n : Nota.findByEstudiante(e.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo_estudiante", e.codigo);
            m.put("curso", n.curso);
            m.put("parcial", n.parcial);
            m.put("final", n.notaFinal);
            m.put("promedio", n.promedio);
            lista.add(m);
        }
        return lista;
    }

    private List<Map<String, Object>> cursos(Estudiante e) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Curso c : Curso.findByEstudiante(e.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo_estudiante", e.codigo);
            m.put("codigo_curso", c.codigoCurso);
            m.put("nombre_curso", c.nombreCurso);
            m.put("creditos", c.creditos);
            m.put("estado", c.estado);
            // Campos que aporta la extensión "UTPBot Sync". Sin exponerlos acá, la IA
            // no tiene forma de saber la modalidad y termina infiriéndola mal (ej.
            // deducir "es virtual" solo porque el aula viene vacía).
            m.put("modalidad", c.modalidad);
            m.put("progreso_porcentaje", c.progreso);
            m.put("docente", c.docente);
            lista.add(m);
        }
        return lista;
    }

    private List<Map<String, Object>> examenes(Estudiante e) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Examen ex : Examen.findByEstudiante(e.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo_estudiante", e.codigo);
            m.put("curso", ex.curso);
            m.put("tipo", ex.tipo);
            m.put("fecha", ex.fecha.toString());
            m.put("hora", ex.hora != null ? ex.hora.toString() : "");
            m.put("aula", ex.aula);
            lista.add(m);
        }
        return lista;
    }

    private List<Map<String, Object>> asistencia(Estudiante e) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Asistencia a : Asistencia.findByEstudiante(e.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo_estudiante", e.codigo);
            m.put("curso", a.curso);
            m.put("fecha", a.fecha.toString());
            m.put("estado", a.estado);
            lista.add(m);
        }
        return lista;
    }

    private List<Map<String, Object>> avances(Estudiante e) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (AvanceTrabajo av : AvanceTrabajo.findByEstudiante(e.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo_estudiante", e.codigo);
            m.put("curso", av.curso);
            m.put("entregable", av.entregable);
            m.put("fecha_entrega", av.fechaEntrega != null ? av.fechaEntrega.toString() : "");
            m.put("estado", av.estado);
            m.put("nota", av.nota);
            lista.add(m);
        }
        return lista;
    }

    private List<Map<String, Object>> proyectos(Estudiante e) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (ProyectoFinal p : ProyectoFinal.findByEstudiante(e.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo_estudiante", e.codigo);
            m.put("curso", p.curso);
            m.put("titulo", p.titulo);
            m.put("grupo", p.grupo);
            m.put("fecha_sustentacion", p.fechaSustentacion != null ? p.fechaSustentacion.toString() : "");
            m.put("nota", p.nota);
            lista.add(m);
        }
        return lista;
    }

    private List<Map<String, Object>> calendario(String aplicaA) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (EventoCalendario ev : EventoCalendario.findAplicaA(aplicaA)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fecha", ev.fecha.toString());
            m.put("evento", ev.evento);
            m.put("descripcion", ev.descripcion);
            m.put("aplica_a", ev.aplicaA);
            lista.add(m);
        }
        return lista;
    }

    // ===================== DATOS DEL DOCENTE =====================

    private List<Map<String, Object>> secciones(Docente d) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (SeccionDocente s : SeccionDocente.findByDocente(d.id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo_docente", d.codigo);
            m.put("curso", s.curso);
            m.put("seccion", s.seccion);
            // Reconstruye el mismo shape que Sheets tenía (string JSON de códigos) — ahora
            // la fuente real es la relación M:N secciones_docente_estudiantes.
            List<String> codigos = new ArrayList<>();
            for (Estudiante est : s.estudiantes) {
                codigos.add(est.codigo);
            }
            m.put("lista_estudiantes", codigos);
            m.put("horario", s.horario);
            lista.add(m);
        }
        return lista;
    }

    /**
     * Equivalente a obtener_datos_estudiantes_seccion(): notas/asistencia filtradas al
     * curso de cada sección. Público (a diferencia del resto de helpers privados de
     * este servicio) porque DocenteResource lo reutiliza tal cual para GET
     * /docente/seccion/{codigo}.
     */
    @Transactional
    public List<Map<String, Object>> datosEstudiantesSeccion(Docente d) {
        List<Map<String, Object>> resultado = new ArrayList<>();

        for (SeccionDocente s : SeccionDocente.findByDocente(d.id)) {
            List<Map<String, Object>> estudiantesData = new ArrayList<>();

            for (Estudiante est : s.estudiantes) {
                List<Map<String, Object>> notasCurso = new ArrayList<>();
                for (Nota n : Nota.findByEstudiante(est.id)) {
                    if (s.curso.equals(n.curso)) {
                        Map<String, Object> nm = new LinkedHashMap<>();
                        nm.put("curso", n.curso);
                        nm.put("parcial", n.parcial);
                        nm.put("final", n.notaFinal);
                        nm.put("promedio", n.promedio);
                        notasCurso.add(nm);
                    }
                }

                List<Map<String, Object>> asistenciaCurso = new ArrayList<>();
                for (Asistencia a : Asistencia.findByEstudiante(est.id)) {
                    if (s.curso.equals(a.curso)) {
                        Map<String, Object> am = new LinkedHashMap<>();
                        am.put("fecha", a.fecha.toString());
                        am.put("estado", a.estado);
                        asistenciaCurso.add(am);
                    }
                }

                Map<String, Object> em = new LinkedHashMap<>();
                em.put("codigo", est.codigo);
                em.put("nombre", est.nombre);
                em.put("notas", notasCurso);
                em.put("asistencia", asistenciaCurso);
                estudiantesData.add(em);
            }

            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("curso", s.curso);
            sm.put("seccion", s.seccion);
            sm.put("horario", s.horario);
            sm.put("estudiantes", estudiantesData);
            resultado.add(sm);
        }

        return resultado;
    }

    private static String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
