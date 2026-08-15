package com.utpbot.resource;

import com.utpbot.entity.Docente;
import com.utpbot.entity.SeccionDocente;
import com.utpbot.exception.ApiException;
import com.utpbot.security.CurrentUser;
import com.utpbot.service.AcademicDataService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Equivalente a routes/docente.py — mismo path raíz "/docente". Cada handler exige
 * además que el docente autenticado esté consultando SU PROPIO código (igual chequeo
 * "solo tu propio código" que el original, 403 si no coincide). Solo rol "docente" —
 * el Python original tampoco dejaba pasar a "admin" aquí, se replica tal cual.
 */
@Path("/docente")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("docente")
public class DocenteResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    AcademicDataService academicDataService;

    @GET
    @Path("/seccion/{codigoDocente}")
    @Transactional
    public Map<String, Object> seccion(@PathParam("codigoDocente") String codigoDocente) {
        exigirPropioCodigo(codigoDocente, "Solo puedes consultar tus propias secciones.");

        Docente docente = Docente.findByCodigo(codigoDocente);
        if (docente == null) {
            throw ApiException.notFound("Docente no encontrado.");
        }

        List<Map<String, Object>> secciones = academicDataService.datosEstudiantesSeccion(docente);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("codigo_docente", codigoDocente);
        if (secciones.isEmpty()) {
            respuesta.put("secciones", secciones);
            respuesta.put("message", "No se encontraron secciones asignadas para este docente.");
        } else {
            respuesta.put("total_secciones", secciones.size());
            respuesta.put("secciones", secciones);
        }
        return respuesta;
    }

    @GET
    @Path("/resumen/{codigoDocente}")
    @Transactional
    public Map<String, Object> resumen(@PathParam("codigoDocente") String codigoDocente) {
        exigirPropioCodigo(codigoDocente, "Solo puedes consultar tu propio resumen.");

        Docente docente = Docente.findByCodigo(codigoDocente);
        if (docente == null) {
            throw ApiException.notFound("Docente no encontrado.");
        }

        List<SeccionDocente> secciones = SeccionDocente.findByDocente(docente.id);
        int totalAlumnos = secciones.stream().mapToInt(s -> s.estudiantes.size()).sum();
        LinkedHashSet<String> cursos = new LinkedHashSet<>();
        secciones.forEach(s -> cursos.add(s.curso));

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("codigo_docente", docente.codigo);
        respuesta.put("nombre", docente.nombre);
        respuesta.put("departamento", docente.departamento);
        respuesta.put("total_secciones", secciones.size());
        respuesta.put("total_alumnos", totalAlumnos);
        respuesta.put("cursos", cursos);
        return respuesta;
    }

    /**
     * Mismo chequeo que routes/docente.py, con el MISMO texto exacto por endpoint
     * (verificado contra el original: seccion() usa "...propias secciones.",
     * resumen() usa "...propio resumen." — dos mensajes distintos, no uno genérico).
     */
    private void exigirPropioCodigo(String codigoSolicitado, String mensajeError) {
        if (!currentUser.codigo().equals(codigoSolicitado)) {
            throw ApiException.forbidden(mensajeError);
        }
    }
}
