package com.utpbot.exception;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Normaliza TODA respuesta de error a {"detail": "..."} — el mismo shape que genera
 * FastAPI por defecto (HTTPException) en el backend Python actual, del cual el
 * frontend depende literalmente:
 *   - script.js lee `data.detail` verbatim en el fallo de login.
 *   - admin.js cierra sesión ante cualquier 401/403, sin mirar el body.
 * Ver plan de migración, sección "Autenticación", punto 9.
 */
public class ApiExceptionMapper {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @ServerExceptionMapper
    public Response mapApiException(ApiException e) {
        return detail(e.getStatus(), e.getMessage());
    }

    /** Lanzada por @RolesAllowed cuando no hay token / token inválido (equivalente a jwt_utils.verificar_token). */
    @ServerExceptionMapper
    public Response mapUnauthorized(UnauthorizedException e) {
        return detail(401, "No autenticado. Inicia sesión nuevamente.");
    }

    /** Lanzada por @RolesAllowed cuando el rol no corresponde (equivalente a verificar_admin/verificar_docente_o_admin). */
    @ServerExceptionMapper
    public Response mapForbidden(ForbiddenException e) {
        return detail(403, "Acceso denegado. No tienes el rol requerido para este recurso.");
    }

    /** Validación de DTOs de entrada (Hibernate Validator) — equivalente a los 422 automáticos de Pydantic en FastAPI. */
    @ServerExceptionMapper
    public Response mapValidation(ConstraintViolationException e) {
        String mensaje = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return detail(422, mensaje.isBlank() ? "Datos de entrada inválidos." : mensaje);
    }

    /** Catch-all — nunca se filtra el mensaje interno de la excepción al cliente. */
    @ServerExceptionMapper
    public Response mapUnexpected(Throwable e) {
        LOG.error("Error no controlado", e);
        return detail(500, "Error interno del servidor.");
    }

    private static Response detail(int status, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("detail", message);
        return Response.status(status).entity(body).build();
    }
}
