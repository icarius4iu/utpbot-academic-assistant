package com.utpbot.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Equivalente a las rutas raíz de main.py (GET "/" y GET "/health").
 * Usado por Railway como healthcheckPath (railway.toml).
 */
@Path("/")
public class HealthResource {

    private static final String VERSION = "3.0.0";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "UTPBot API");
        body.put("version", VERSION);
        body.put("estado", "✅ Activo");
        body.put("descripcion", "API del Asistente Académico Virtual de la UTP (backend Quarkus).");
        body.put("documentacion", "/q/swagger-ui");
        return body;
    }

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "utpbot-api");
        body.put("version", VERSION);
        return body;
    }
}
