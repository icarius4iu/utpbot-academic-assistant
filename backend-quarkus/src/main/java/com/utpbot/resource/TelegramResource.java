package com.utpbot.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.utpbot.exception.ApiException;
import com.utpbot.service.TelegramService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Equivalente a routes/telegram.py — mismo path raíz "/telegram". Igual que el
 * original en Python, NINGUNO de estos 3 endpoints exige rol admin explícitamente
 * (se replica tal cual; admin.js solo los llama desde una sesión ya autenticada, pero
 * el backend en sí no lo obliga — comportamiento heredado, no una corrección
 * deliberada de este plan de migración).
 */
@Path("/telegram")
@Produces(MediaType.APPLICATION_JSON)
public class TelegramResource {

    private static final Logger LOG = Logger.getLogger(TelegramResource.class);

    @Inject
    TelegramService telegramService;

    /**
     * Webhook público de Telegram. Debe responder dentro de la ventana de 5s de
     * Telegram — el procesamiento real corre en un hilo virtual aparte (equivalente a
     * BackgroundTasks de FastAPI), sin bloquear esta respuesta.
     */
    @POST
    @Path("/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> webhook(JsonNode body) {
        Map<String, Object> ok = Map.of("ok", true);
        if (body == null) {
            return ok;
        }

        JsonNode message = body.has("message") ? body.get("message")
                : body.has("edited_message") ? body.get("edited_message") : null;
        if (message == null) {
            return ok; // callback_query u otro tipo de update — se ignora, igual que en Python
        }

        JsonNode chatNode = message.path("chat").path("id");
        JsonNode textNode = message.path("text");
        if (chatNode.isMissingNode() || textNode.isMissingNode()) {
            return ok;
        }

        long chatId = chatNode.asLong();
        String texto = textNode.asText();

        Thread.startVirtualThread(() -> {
            try {
                telegramService.procesarMensaje(chatId, texto);
            } catch (Exception e) {
                LOG.errorf("Error procesando mensaje de Telegram (chat %d): %s", chatId, e.getMessage());
            }
        });

        return ok;
    }

    /** Registra el webhook ante Telegram — mismo comportamiento en GET y POST que el original. */
    @GET
    @Path("/setup-webhook")
    public Map<String, Object> setupWebhookGet() {
        return setupWebhook();
    }

    @POST
    @Path("/setup-webhook")
    public Map<String, Object> setupWebhookPost() {
        return setupWebhook();
    }

    private Map<String, Object> setupWebhook() {
        if (!telegramService.tokenConfigurado()) {
            throw ApiException.serviceUnavailable("TELEGRAM_BOT_TOKEN no configurado.");
        }
        String webhookUrl = telegramService.webhookUrlConfigurado();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw ApiException.serviceUnavailable("TELEGRAM_WEBHOOK_URL no configurado.");
        }

        Map<String, Object> resultado = telegramService.configurarWebhook(webhookUrl);
        boolean success = Boolean.TRUE.equals(resultado.get("ok"));

        Map<String, Object> respuesta = new LinkedHashMap<>();
        if (success) {
            respuesta.put("success", true);
            respuesta.put("message", "Webhook configurado correctamente en: " + webhookUrl);
            respuesta.put("result", resultado);
            return respuesta;
        }

        String detalle = String.valueOf(resultado.getOrDefault("description", "Error desconocido"));
        throw ApiException.internal(detalle);
    }

    @GET
    @Path("/status")
    public Map<String, Object> status() {
        return telegramService.obtenerEstado();
    }
}
