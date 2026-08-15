package com.utpbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Equivalente a services/telegram_service.py. El bot es EXCLUSIVAMENTE de
 * notificaciones (no chatea de forma interactiva) — igual que hoy; no se resucita el
 * diseño interactivo que sugieren los imports muertos de gemini_service/sheets_service
 * en el Python original (ver plan de migración, sección "Integración con Gemini
 * (Java)": "TelegramService — mismo diseño notification-only").
 */
@ApplicationScoped
public class TelegramService {

    private static final Logger LOG = Logger.getLogger(TelegramService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final String MENSAJE_BIENVENIDA = """

            🤖 *Bot de Mensajería y Notificaciones UTP IA* 🎓

            ¡Hola! Este canal sirve exclusivamente para enviarte **notificaciones automáticas, alertas y confirmaciones** en tiempo real. \s

            Por ejemplo, cuando uses el Asistente UTP IA en la Web para organizar tu tiempo de estudio, este bot te enviará un mensaje de confirmación cuando los bloques de estudio sean añadidos a tu **Google Calendar** 📅.

            🌐 *¿Quieres chatear con la IA o planificar tus horarios?*
            Accede ahora a la plataforma web oficial de **UTP IA**.
            """;

    private static final String MENSAJE_AYUDA = """

            🤖 *Bot de Notificaciones UTP IA*

            Este bot está configurado en modo **Notificación**. No procesa consultas interactivas de forma directa en Telegram para garantizar un canal limpio y libre de spam.

            📌 *¿Cómo funciona?*
            1. Entra al sitio web del **Asistente Académico UTP IA**.
            2. Planifica tus sesiones de estudio o revisa tus exámenes.
            3. Al agendar tus sesiones de estudio en **Google Calendar**, recibirás una alerta de confirmación instantánea aquí.
            """;

    private static final String RECORDATORIO =
            "🤖 *Bot de Notificaciones UTP IA* 🎓\n\n"
            + "Hola. Este canal está reservado exclusivamente para el envío de **notificaciones, alertas y confirmaciones** automáticas.\n\n"
            + "Si deseas chatear con la Inteligencia Artificial, organizar tus horarios de estudio en Google Calendar o revisar tu avance, ingresa a la plataforma web oficial 🌐.";

    @ConfigProperty(name = "telegram.bot-token")
    Optional<String> botToken;

    @ConfigProperty(name = "telegram.webhook-url")
    Optional<String> webhookUrl;

    @ConfigProperty(name = "telegram.notifications-chat-id")
    Optional<String> notificationsChatId;

    private String apiBase() {
        return "https://api.telegram.org/bot" + botToken.orElse("");
    }

    public boolean enviarMensaje(long chatId, String texto, String parseMode) {
        if (botToken.isEmpty() || botToken.get().isBlank()) {
            LOG.warn("TELEGRAM_BOT_TOKEN no configurado");
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", texto);
        payload.put("parse_mode", parseMode);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase() + "/sendMessage"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            LOG.errorf("Error enviando mensaje Telegram a %d: %s", chatId, e.getMessage());
            return false;
        }
    }

    /** Equivalente a routes/telegram.py: GET /telegram/status. */
    public Map<String, Object> obtenerEstado() {
        boolean tokenConfigurado = botToken.isPresent() && !botToken.get().isBlank()
                && !botToken.get().equals("your_telegram_bot_token_here");
        boolean webhookConfigurado = webhookUrl.isPresent() && !webhookUrl.get().isBlank()
                && !webhookUrl.get().contains("your-backend");
        boolean chatIdConfigurado = notificationsChatId.isPresent() && !notificationsChatId.get().isBlank()
                && !notificationsChatId.get().equals("your_telegram_chat_id_here");

        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("token_configurado", tokenConfigurado);
        estado.put("webhook_configurado", webhookConfigurado);
        // "No configurado" (no null) -- verificado contra el original (routes/telegram.py
        // líneas 103/105): admin.js muestra webhook_url tal cual en el DOM, y un null
        // literal renderizaría como el texto "null" en la UI.
        estado.put("webhook_url", webhookConfigurado ? webhookUrl.get() : "No configurado");
        estado.put("chat_id_configurado", chatIdConfigurado);
        estado.put("chat_id_notificaciones", chatIdConfigurado ? notificationsChatId.get() : "No configurado");
        estado.put("listo", tokenConfigurado && webhookConfigurado && chatIdConfigurado);
        return estado;
    }

    public String webhookUrlConfigurado() {
        return webhookUrl.orElse(null);
    }

    public boolean tokenConfigurado() {
        return botToken.isPresent() && !botToken.get().isBlank();
    }

    /** Configura el webhook de Telegram — POST /setWebhook con allowed_updates=[message,callback_query]. */
    public Map<String, Object> configurarWebhook(String webhookUrl) {
        if (botToken.isEmpty() || botToken.get().isBlank()) {
            return Map.of("ok", false, "description", "Token no configurado");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("url", webhookUrl);
            payload.put("allowed_updates", List.of("message", "callback_query"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase() + "/setWebhook"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return JSON.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("ok", false, "description", String.valueOf(e.getMessage()));
        }
    }

    /** Modo notificación: /start,/inicio → bienvenida; /ayuda,/help → ayuda; cualquier otro → recordatorio. */
    public void procesarMensaje(long chatId, String texto) {
        String textoLimpio = texto == null ? "" : texto.strip().toLowerCase();

        if (textoLimpio.equals("/start") || textoLimpio.equals("/inicio")) {
            enviarMensaje(chatId, MENSAJE_BIENVENIDA, "Markdown");
            return;
        }
        if (textoLimpio.equals("/ayuda") || textoLimpio.equals("/help")) {
            enviarMensaje(chatId, MENSAJE_AYUDA, "Markdown");
            return;
        }
        enviarMensaje(chatId, RECORDATORIO, "Markdown");
    }

    /** Confirmación de agendado — misma plantilla exacta que Python, mismo fallback ante fecha ISO no parseable. */
    public boolean enviarConfirmacionEstudio(String titulo, String fechaInicio, String fechaFin, String descripcion) {
        if (notificationsChatId.isEmpty() || notificationsChatId.get().isBlank()) {
            LOG.warn("TELEGRAM_NOTIFICATIONS_CHAT_ID no configurado.");
            return false;
        }

        long targetChatId;
        try {
            targetChatId = Long.parseLong(notificationsChatId.get());
        } catch (NumberFormatException e) {
            LOG.errorf("TELEGRAM_NOTIFICATIONS_CHAT_ID no es un entero válido: %s", notificationsChatId.get());
            return false;
        }

        String fechaLegible;
        String horaInicio;
        String horaFin;
        try {
            String fechaStr = fechaInicio.split("T")[0];
            horaInicio = fechaInicio.split("T")[1].substring(0, 5);
            horaFin = fechaFin.split("T")[1].substring(0, 5);
            String[] partes = fechaStr.split("-");
            fechaLegible = partes[2] + "/" + partes[1] + "/" + partes[0];
        } catch (Exception e) {
            fechaLegible = fechaInicio;
            horaInicio = "Ver evento";
            horaFin = "Ver evento";
        }

        String mensaje = "📅 *¡NUEVO BLOQUE DE ESTUDIO AGENDADO!* 📅\n\n"
                + "Hola, el *Asistente Académico UTP IA* ha reservado un bloque de tiempo de estudio en tu **Google Calendar**:\n\n"
                + "📖 *Tema:* `" + titulo + "`\n"
                + "📅 *Fecha:* " + fechaLegible + "\n"
                + "⏰ *Horario:* " + horaInicio + " - " + horaFin + "\n\n"
                + "📝 *Detalles:* " + (descripcion == null || descripcion.isBlank() ? "Sin descripción adicional." : descripcion) + "\n\n"
                + "🚀 _¡Mucho éxito en tu sesión! Organizar tu tiempo con anticipación te garantizará mejores resultados en tus evaluaciones._";

        return enviarMensaje(targetChatId, mensaje, "Markdown");
    }
}
