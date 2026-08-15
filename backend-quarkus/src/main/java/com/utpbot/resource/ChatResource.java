package com.utpbot.resource;

import com.utpbot.dto.chat.ChatRequest;
import com.utpbot.dto.chat.ChatResponse;
import com.utpbot.entity.ConsultaLog;
import com.utpbot.exception.ApiException;
import com.utpbot.security.CurrentUser;
import com.utpbot.service.AcademicDataService;
import com.utpbot.service.GeminiResult;
import com.utpbot.service.GeminiService;
import com.utpbot.service.PromptBuilderService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Equivalente a routes/chat.py: POST /chat. Mismo path raíz que el frontend ya
 * tiene hardcodeado (sin prefijo).
 */
@Path("/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChatResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    AcademicDataService academicDataService;

    @Inject
    PromptBuilderService promptBuilderService;

    @Inject
    GeminiService geminiService;

    @POST
    @RolesAllowed({"estudiante", "docente", "admin"})
    @Transactional
    public ChatResponse chat(@Valid ChatRequest request) {
        // Corrige el bug de autorización de routes/chat.py: allí se confiaba
        // ciegamente en codigo_usuario/rol del BODY. Aquí se cruzan contra el
        // principal autenticado (ver plan, sección "Autenticación", punto 7).
        if (!currentUser.codigo().equals(request.codigoUsuario) || !currentUser.rol().equals(request.rol)) {
            throw ApiException.forbidden("No autorizado a operar como otro usuario.");
        }

        String rol = currentUser.rol();
        String codigo = currentUser.codigo();
        String nombre = currentUser.nombre();

        Map<String, Object> datosUsuario = "estudiante".equals(rol)
                ? academicDataService.recopilarDatosEstudiante(codigo)
                : academicDataService.recopilarDatosDocente(codigo);

        boolean esPrimerMensaje = request.historial == null || request.historial.isEmpty();
        String idioma = request.idiomaPreferido == null || request.idiomaPreferido.isBlank()
                ? "es" : request.idiomaPreferido;

        String systemPrompt = promptBuilderService.construirPromptSistema(
                rol, nombre, datosUsuario, idioma, esPrimerMensaje);

        GeminiResult resultado = geminiService.generarRespuesta(
                systemPrompt, request.mensaje, request.historial,
                request.fileName, request.fileMime, request.fileData);

        String categoria = geminiService.categorizarPregunta(request.mensaje);
        registrarConsulta(codigo, rol, request.mensaje, categoria);

        return new ChatResponse(resultado.respuesta(), resultado.sugerencias());
    }

    private void registrarConsulta(String codigo, String rol, String pregunta, String categoria) {
        try {
            ConsultaLog log = new ConsultaLog();
            log.fecha = OffsetDateTime.now();
            log.codigoUsuario = codigo;
            log.rol = rol;
            log.pregunta = pregunta;
            log.categoria = categoria;
            log.persist();
        } catch (Exception e) {
            // Igual que Python (registrar_faq): nunca interrumpir el flujo de chat si falla el logging.
        }
    }
}
