package com.utpbot.resource;

import com.utpbot.dto.estudio.*;
import com.utpbot.entity.*;
import com.utpbot.exception.ApiException;
import com.utpbot.security.CurrentUser;
import com.utpbot.service.EstudioMetaService;
import com.utpbot.service.EstudioService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Módulo de estudio personalizado. Todo bajo rol "estudiante": cada alumno solo ve y
 * modifica lo suyo (el código sale del token verificado, nunca del body/path).
 */
@Path("/estudio")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("estudiante")
public class EstudioResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    EstudioService estudioService;

    @Inject
    EstudioMetaService metaService;

    private Estudiante estudianteActual() {
        Estudiante e = Estudiante.findByCodigo(currentUser.codigo());
        if (e == null) {
            throw ApiException.notFound("No existe un estudiante con tu código en UTPBot.");
        }
        return e;
    }

    // ===================== MATERIALES =====================

    @POST
    @Path("/materiales")
    public MaterialDto subirMaterial(@Valid SubirMaterialRequest request) {
        return MaterialDto.de(estudioService.subirMaterial(estudianteActual(), request));
    }

    @GET
    @Path("/materiales")
    @Transactional
    public List<MaterialDto> listarMateriales() {
        List<MaterialDto> lista = new ArrayList<>();
        MaterialEstudio.findByEstudiante(estudianteActual().id).forEach(m -> lista.add(MaterialDto.de(m)));
        return lista;
    }

    @GET
    @Path("/materiales/{id}")
    @Transactional
    public MaterialDto verMaterial(@PathParam("id") Long id) {
        MaterialEstudio m = MaterialEstudio.findPropio(id, estudianteActual().id);
        if (m == null) throw ApiException.notFound("Material no encontrado.");
        return MaterialDto.de(m);
    }

    @DELETE
    @Path("/materiales/{id}")
    public Map<String, Object> eliminarMaterial(@PathParam("id") Long id) {
        estudioService.eliminarMaterial(id, estudianteActual().id);
        return Map.of("ok", true);
    }

    // ===================== HISTORIA 1: RUTA DE ESTUDIO =====================

    /** Genera la ruta de estudio a partir de un sílabo ya subido. */
    @POST
    @Path("/materiales/{id}/ruta")
    @Transactional
    public RutaEstudioDto generarRuta(@PathParam("id") Long materialId) {
        RutaEstudio ruta = estudioService.generarRuta(materialId, estudianteActual());
        return RutaEstudioDto.de(ruta, TemaRuta.findByRuta(ruta.id));
    }

    @GET
    @Path("/rutas")
    @Transactional
    public List<Map<String, Object>> listarRutas() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (RutaEstudio r : RutaEstudio.findByEstudiante(estudianteActual().id)) {
            List<TemaRuta> temas = TemaRuta.findByRuta(r.id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("curso", r.curso);
            m.put("titulo", r.titulo);
            m.put("created_at", r.createdAt.toString());
            m.put("total_temas", temas.size());
            m.put("temas_completados", temas.stream().filter(t -> Boolean.TRUE.equals(t.completado)).count());
            lista.add(m);
        }
        return lista;
    }

    @GET
    @Path("/rutas/{id}")
    @Transactional
    public RutaEstudioDto verRuta(@PathParam("id") Long id) {
        RutaEstudio r = RutaEstudio.findPropia(id, estudianteActual().id);
        if (r == null) throw ApiException.notFound("Ruta no encontrada.");
        return RutaEstudioDto.de(r, TemaRuta.findByRuta(r.id));
    }

    /** Marca (o desmarca) un tema como completado. */
    @PATCH
    @Path("/temas/{id}")
    public Map<String, Object> marcarTema(@PathParam("id") Long id, Map<String, Object> body) {
        boolean completado = body == null || !body.containsKey("completado")
                || Boolean.parseBoolean(String.valueOf(body.get("completado")));
        TemaRuta tema = estudioService.marcarTema(id, estudianteActual().id, completado);
        return Map.of("id", tema.id, "completado", tema.completado);
    }

    // ===================== HISTORIA 2: CUESTIONARIOS Y RESÚMENES =====================

    @POST
    @Path("/materiales/{id}/cuestionario")
    @Transactional
    public CuestionarioDto generarCuestionario(@PathParam("id") Long materialId,
                                                @QueryParam("preguntas") @DefaultValue("8") int preguntas) {
        Cuestionario c = estudioService.generarCuestionario(materialId, estudianteActual(), preguntas);
        return CuestionarioDto.de(c, PreguntaCuestionario.findByCuestionario(c.id));
    }

    @GET
    @Path("/cuestionarios")
    @Transactional
    public List<Map<String, Object>> listarCuestionarios() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Cuestionario c : Cuestionario.findByEstudiante(estudianteActual().id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("titulo", c.titulo);
            m.put("created_at", c.createdAt.toString());
            m.put("total_preguntas", PreguntaCuestionario.findByCuestionario(c.id).size());
            lista.add(m);
        }
        return lista;
    }

    @GET
    @Path("/cuestionarios/{id}")
    @Transactional
    public CuestionarioDto verCuestionario(@PathParam("id") Long id) {
        Cuestionario c = Cuestionario.findPropio(id, estudianteActual().id);
        if (c == null) throw ApiException.notFound("Cuestionario no encontrado.");
        return CuestionarioDto.de(c, PreguntaCuestionario.findByCuestionario(c.id));
    }

    @POST
    @Path("/materiales/{id}/resumen")
    @Transactional
    public Map<String, Object> generarResumen(@PathParam("id") Long materialId) {
        ResumenMaterial r = estudioService.generarResumen(materialId, estudianteActual());
        return Map.of("id", r.id, "contenido", r.contenido, "created_at", r.createdAt.toString());
    }

    // ===================== HISTORIA 3: METAS Y RACHA =====================

    @GET
    @Path("/meta")
    public MetaEstudioDto verMeta() {
        return metaService.obtenerRacha(estudianteActual());
    }

    @PUT
    @Path("/meta")
    public MetaEstudioDto establecerMeta(@Valid EstablecerMetaRequest request) {
        Estudiante e = estudianteActual();
        metaService.establecerMeta(e, request.minutosDiarios);
        return metaService.obtenerRacha(e);
    }

    @POST
    @Path("/sesiones")
    public MetaEstudioDto registrarSesion(@Valid RegistrarSesionRequest request) {
        Estudiante e = estudianteActual();
        metaService.registrarSesion(e, request);
        return metaService.obtenerRacha(e);
    }

    @GET
    @Path("/racha")
    public MetaEstudioDto verRacha() {
        return metaService.obtenerRacha(estudianteActual());
    }
}
