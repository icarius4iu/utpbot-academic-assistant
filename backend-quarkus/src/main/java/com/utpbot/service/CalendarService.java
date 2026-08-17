package com.utpbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Equivalente a services/calendar_service.py: crear_evento_estudio(). Igual que en
 * Python, se usa la cuenta de servicio SOLO para obtener un access token OAuth2 y se
 * llama a la REST API de Calendar directamente (no el SDK completo de Google Calendar)
 * — ver plan de migración, sección "Integración con Gemini (Java)".
 */
@ApplicationScoped
public class CalendarService {

    private static final Logger LOG = Logger.getLogger(CalendarService.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Optional: ver la nota en FirebaseAdminInitializer sobre el arranque. */
    @ConfigProperty(name = "firebase.credentials.json")
    Optional<String> credentialsJson;

    @ConfigProperty(name = "google.calendar.id")
    String calendarId;

    private GoogleCredentials credenciales;

    private synchronized GoogleCredentials credenciales() throws Exception {
        if (credenciales == null) {
            GoogleCredentials base = (credentialsJson.isPresent() && !credentialsJson.get().isBlank())
                    ? GoogleCredentials.fromStream(new ByteArrayInputStream(credentialsJson.get().getBytes(StandardCharsets.UTF_8)))
                    : GoogleCredentials.getApplicationDefault();
            credenciales = base.createScoped(SCOPE);
        }
        return credenciales;
    }

    /**
     * Crea un evento real en Google Calendar. Misma regla de zona horaria que Python:
     * si el ISO datetime no trae offset explícito (Z/+/-), se asume Lima (-05:00).
     */
    public JsonNode crearEventoEstudio(String titulo, String fechaInicio, String fechaFin, String descripcion) throws Exception {
        String token;
        try {
            token = credenciales().refreshAccessToken().getTokenValue();
        } catch (Exception e) {
            throw new Exception("No se pudo autenticar con los servidores de Google Calendar.", e);
        }

        String inicio = conOffsetLima(fechaInicio);
        String fin = conOffsetLima(fechaFin);

        Map<String, Object> start = new LinkedHashMap<>();
        start.put("dateTime", inicio);
        start.put("timeZone", "America/Lima");

        Map<String, Object> end = new LinkedHashMap<>();
        end.put("dateTime", fin);
        end.put("timeZone", "America/Lima");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", titulo);
        body.put("description", descripcion == null ? "" : descripcion);
        body.put("start", start);
        body.put("end", end);
        body.put("reminders", Map.of("useDefault", true));

        String url = "https://www.googleapis.com/calendar/v3/calendars/"
                + java.net.URLEncoder.encode(calendarId, StandardCharsets.UTF_8) + "/events";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            String errorMsg = extraerMensajeError(response.body());
            throw new Exception("Google Calendar API Error: " + errorMsg);
        }

        return JSON.readTree(response.body());
    }

    /** Igual regla que Python: sin offset explícito tras la posición 10 (fecha) → asume "-05:00" (Lima). */
    private static String conOffsetLima(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.length() <= 10) {
            return isoDateTime;
        }
        String resto = isoDateTime.substring(10);
        boolean tieneOffset = resto.contains("Z") || resto.contains("+") || resto.contains("-");
        return tieneOffset ? isoDateTime : isoDateTime + "-05:00";
    }

    private String extraerMensajeError(String body) {
        try {
            JsonNode node = JSON.readTree(body);
            JsonNode error = node.path("error").path("message");
            return error.isMissingNode() ? body : error.asText();
        } catch (Exception e) {
            return body;
        }
    }
}
