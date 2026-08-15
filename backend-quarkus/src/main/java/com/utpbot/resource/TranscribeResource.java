package com.utpbot.resource;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.utpbot.dto.chat.TranscribeRequest;
import com.utpbot.dto.chat.TranscribeResponse;
import com.utpbot.exception.ApiException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Equivalente a routes/transcribe.py — mismo path raíz "/transcribe". Usa un cliente
 * Gemini PROPIO (no el de GeminiService) y un modelo fijo sin fallback, igual que el
 * original — ver plan de migración, sección "Integración con Gemini (Java)".
 */
@Path("/transcribe")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TranscribeResource {

    private static final Logger LOG = Logger.getLogger(TranscribeResource.class);
    private static final String MODELO = "gemini-1.5-flash";
    private static final List<String> RESPUESTAS_SILENCIO = List.of("[SILENCIO]", "SILENCIO", "[SILENCE]", "SILENCE");

    private static final String PROMPT_TRANSCRIPCION =
            "Eres un transcriptor preciso. Transcribe exactamente lo que dice la persona en el siguiente audio. "
            + "Responde SOLO con el texto transcrito, sin explicaciones, sin comillas y sin signos adicionales. "
            + "Si el audio está en español, transcribe en español. Si no se escucha nada o hay demasiado ruido, "
            + "responde únicamente con: [SILENCIO]";

    @ConfigProperty(name = "gemini.api.key")
    Optional<String> apiKey;

    @POST
    @RolesAllowed({"estudiante", "docente", "admin"})
    public TranscribeResponse transcribe(@Valid TranscribeRequest request) {
        if (apiKey.isEmpty() || apiKey.get().isBlank()) {
            throw ApiException.serviceUnavailable("GEMINI_API_KEY no configurada.");
        }

        byte[] audioBytes;
        try {
            audioBytes = Base64.getDecoder().decode(request.audioBase64);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("audio_base64 no es un base64 válido.");
        }

        if (audioBytes.length < 1000) {
            throw ApiException.badRequest("Audio muy corto o vacío.");
        }

        try {
            Client client = Client.builder().apiKey(apiKey.get()).build();
            String mimeType = request.mimeType == null || request.mimeType.isBlank() ? "audio/webm" : request.mimeType;

            GenerateContentResponse response = client.models.generateContent(
                    MODELO,
                    Content.fromParts(
                            Part.fromBytes(audioBytes, mimeType),
                            Part.fromText(PROMPT_TRANSCRIPCION)),
                    null);

            String texto = response.text() == null ? "" : response.text().strip();
            boolean esSilencio = RESPUESTAS_SILENCIO.stream().anyMatch(s -> s.equalsIgnoreCase(texto));

            return new TranscribeResponse(esSilencio ? "" : texto);

        } catch (Exception e) {
            LOG.errorf("Error al transcribir audio: %s", e.getMessage());
            throw ApiException.internal("Error al transcribir el audio: " + e.getMessage());
        }
    }
}
