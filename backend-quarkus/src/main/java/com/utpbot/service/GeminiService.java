package com.utpbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.utpbot.dto.chat.MensajeHistorialDto;
import com.utpbot.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Equivalente a services/gemini_service.py: GeminiService (generar_respuesta,
 * categorizar_pregunta, _extraer_sugerencias, _limpiar_respuesta, _construir_messages).
 * Este es el servicio con más lógica de negocio de todo el sistema — ver plan de
 * migración, sección "Integración con Gemini (Java)" para el detalle punto por punto
 * de qué debe preservarse exacto.
 */
@ApplicationScoped
public class GeminiService {

    private static final Logger LOG = Logger.getLogger(GeminiService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String TOOL_NAME = "agendar_tiempo_estudio";

    // Dos variantes de sugerencias — DEBEN preservarse ambas literalmente (ver plan):
    // la lista por defecto (con tildes) y la que sigue a un agendado exitoso/fallido (sin tildes).
    private static final List<String> SUGERENCIAS_DEFECTO = List.of(
            "¿Cuál es mi horario?", "¿Cuáles son mis notas?", "¿Cuándo es mi próximo examen?");
    private static final List<String> SUGERENCIAS_AGENDADO = List.of(
            "Cual es mi horario?", "Cuales son mis notas?", "Cuando es mi proximo examen?");

    private static final Pattern SUGERENCIAS_PATTERN =
            Pattern.compile("\\{\\s*\"sugerencias\"\\s*:\\s*\\[.*?]\\s*}", Pattern.DOTALL);

    // Mapa ORDENADO palabra-clave -> categoría — el primer match gana, igual que el dict
    // de Python (insertion order = iteration order en Python 3.7+, LinkedHashMap en Java).
    private static final Map<String, List<String>> CATEGORIAS = new LinkedHashMap<>();
    static {
        CATEGORIAS.put("horarios", List.of("horario", "hora", "clase", "aula"));
        CATEGORIAS.put("notas", List.of("nota", "calificación", "promedio", "aprobé"));
        CATEGORIAS.put("examenes", List.of("examen", "parcial", "final", "sustitutorio"));
        CATEGORIAS.put("asistencia", List.of("asistencia", "falta", "tardanza", "ausencia"));
        CATEGORIAS.put("trabajos", List.of("trabajo", "tarea", "entregable", "pendiente"));
        CATEGORIAS.put("proyectos", List.of("proyecto", "sustentación", "grupo"));
        CATEGORIAS.put("calendario", List.of("evento", "calendario", "fecha", "feriado"));
        CATEGORIAS.put("cursos", List.of("curso", "materia", "crédito", "matrícula"));
        CATEGORIAS.put("docente", List.of("profesor", "docente", "sección", "alumnos"));
        CATEGORIAS.put("documentos", List.of("documento", "pdf", "archivo", "excel", "word", "adjunto"));
        CATEGORIAS.put("utp_info", List.of("utp", "universidad", "campus", "sede", "requisito", "admisión"));
    }

    @ConfigProperty(name = "gemini.api.key")
    Optional<String> apiKey;

    @ConfigProperty(name = "gemini.model")
    String modeloConfigurado;

    @Inject
    DocumentExtractionService documentExtractionService;

    @Inject
    CalendarService calendarService;

    @Inject
    TelegramService telegramService;

    private volatile Client client;

    private Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    if (apiKey.isEmpty() || apiKey.get().isBlank()) {
                        throw ApiException.serviceUnavailable(
                                "GEMINI_API_KEY no configurada. Ve a https://aistudio.google.com/app/apikey para obtener tu key.");
                    }
                    client = Client.builder().apiKey(apiKey.get()).build();
                }
            }
        }
        return client;
    }

    /** Orden de fallback idéntico a MODELOS_FALLBACK en Python, deduplicado preservando orden. */
    private List<String> modelosFallback() {
        LinkedHashSet<String> modelos = new LinkedHashSet<>();
        modelos.add(modeloConfigurado == null || modeloConfigurado.isBlank() ? "gemini-2.5-pro" : modeloConfigurado);
        modelos.add("gemini-1.5-pro-latest");
        modelos.add("gemini-2.5-flash");
        modelos.add("gemini-2.0-flash-001");
        modelos.add("gemini-flash-latest");
        return new ArrayList<>(modelos);
    }

    public GeminiResult generarRespuesta(String systemPrompt, String mensajeUsuario,
                                          List<MensajeHistorialDto> historial,
                                          String fileName, String fileMime, String fileData) {
        List<Content> messages = construirMessages(mensajeUsuario, historial, fileName, fileMime, fileData);
        Tool herramientaAgendar = declararHerramientaAgendar();

        String ultimoError = null;

        for (String modelo : modelosFallback()) {
            try {
                LOG.infof("Intentando modelo: %s", modelo);

                GenerateContentResponse response = client().models.generateContent(
                        modelo,
                        messages,
                        GenerateContentConfig.builder()
                                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                                .temperature(0.7f)
                                .maxOutputTokens(8192)
                                .tools(herramientaAgendar)
                                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true).build())
                                .build());

                List<FunctionCall> functionCalls = response.functionCalls();
                if (functionCalls != null && !functionCalls.isEmpty()
                        && TOOL_NAME.equals(functionCalls.get(0).name().orElse(null))) {
                    return ejecutarAgendarTiempoEstudio(functionCalls.get(0));
                }

                String texto = response.text() == null ? "" : response.text().strip();
                String respuestaLimpia = limpiarRespuesta(texto);
                List<String> sugerencias = extraerSugerencias(texto);
                LOG.infof("Respuesta OK con: %s", modelo);
                return new GeminiResult(respuestaLimpia, sugerencias);

            } catch (Exception e) {
                String err = String.valueOf(e.getMessage());
                ultimoError = err;
                String errLower = err.toLowerCase();

                if (errLower.contains("429") || errLower.contains("quota") || errLower.contains("rate")) {
                    LOG.warnf("Cuota agotada en '%s', probando siguiente...", modelo);
                    dormir(1000);
                } else if (errLower.contains("404") || errLower.contains("not found")) {
                    LOG.warnf("Modelo '%s' no disponible, probando siguiente...", modelo);
                } else if (errLower.contains("503") || errLower.contains("unavailable")
                        || errLower.contains("demand") || errLower.contains("500")) {
                    LOG.warnf("Modelo '%s' sobrecargado, probando siguiente...", modelo);
                } else {
                    LOG.errorf("Error en '%s': %s", modelo, err);
                }
                // Igual que Python: SIEMPRE se prueba el siguiente modelo, sea cual sea el error.
            }
        }

        LOG.errorf("Todos los modelos fallaron. Último: %s", ultimoError);
        String ultimoErrorLower = ultimoError == null ? "" : ultimoError.toLowerCase();
        String msg;
        if (ultimoErrorLower.contains("429") || ultimoErrorLower.contains("quota")) {
            msg = "⚠️ **Error 429: Cuota o Facturación.** El servicio de Gemini no procesó la solicitud porque "
                    + "el proyecto de la API Key no tiene saldo o llegó al límite. Detalle: " + truncar(ultimoError, 150);
        } else {
            msg = "❌ Error llamando a Gemini: " + truncar(ultimoError, 150);
        }
        return new GeminiResult(msg, SUGERENCIAS_DEFECTO);
    }

    // ===================== FUNCTION CALLING: agendar_tiempo_estudio =====================

    private Tool declararHerramientaAgendar() {
        Map<String, Schema> propiedades = new LinkedHashMap<>();
        propiedades.put("titulo", Schema.builder().type(Type.Known.STRING)
                .description("Título descriptivo de la materia o tema a estudiar.").build());
        propiedades.put("fecha_inicio", Schema.builder().type(Type.Known.STRING)
                .description("Fecha y hora de inicio en formato ISO 8601 (ej. \"2026-06-03T16:00:00\").").build());
        propiedades.put("fecha_fin", Schema.builder().type(Type.Known.STRING)
                .description("Fecha y hora de fin en formato ISO 8601 (ej. \"2026-06-03T18:00:00\").").build());
        propiedades.put("descripcion", Schema.builder().type(Type.Known.STRING)
                .description("Detalles o temas a estudiar durante la sesión.").build());

        FunctionDeclaration declaracion = FunctionDeclaration.builder()
                .name(TOOL_NAME)
                .description("Agenda una sesión de estudio en el Google Calendar y envía una confirmación por Telegram.")
                .parameters(Schema.builder()
                        .type(Type.Known.OBJECT)
                        .properties(propiedades)
                        .required("titulo", "fecha_inicio", "fecha_fin")
                        .build())
                .build();

        return Tool.builder().functionDeclarations(declaracion).build();
    }

    private GeminiResult ejecutarAgendarTiempoEstudio(FunctionCall functionCall) {
        Map<String, Object> args = functionCall.args().orElse(Map.of());
        String titulo = str(args.get("titulo"));
        String fechaInicio = str(args.get("fecha_inicio"));
        String fechaFin = str(args.get("fecha_fin"));
        String descripcion = str(args.getOrDefault("descripcion", ""));

        LOG.infof("[TOOL] Ejecutando agendar_tiempo_estudio para: %s (%s - %s)", titulo, fechaInicio, fechaFin);

        try {
            calendarService.crearEventoEstudio(titulo, fechaInicio, fechaFin, descripcion);
            telegramService.enviarConfirmacionEstudio(titulo, fechaInicio, fechaFin, descripcion);

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
                horaInicio = "Ver";
                horaFin = "Ver";
            }

            String exitoMsg = "Sesion de estudio agendada con exito.\n\n"
                    + "He reservado el bloque en tu Google Calendar y te he enviado una confirmacion instantanea a tu Telegram:\n\n"
                    + "Materia: " + titulo + "\n"
                    + "Fecha: " + fechaLegible + "\n"
                    + "Horario: " + horaInicio + " - " + horaFin + "\n\n"
                    + "Detalles: " + (descripcion == null || descripcion.isBlank() ? "Sin descripcion adicional." : descripcion) + "\n\n"
                    + "Mucho exito en tu preparacion academica.";

            return new GeminiResult(exitoMsg, SUGERENCIAS_AGENDADO);

        } catch (Exception toolErr) {
            LOG.errorf("[ERROR TOOL] Falló ejecución de herramienta: %s", toolErr.getMessage());
            String errorMsg = "Error al agendar la sesion de estudio: " + toolErr.getMessage() + "\n\n"
                    + "Por favor, verifica que tu calendario este compartido correctamente con la cuenta de servicio o intenta nuevamente.";
            return new GeminiResult(errorMsg, SUGERENCIAS_AGENDADO);
        }
    }

    // ===================== CONSTRUCCIÓN DE MENSAJES (historial + adjuntos) =====================

    private List<Content> construirMessages(String mensajeUsuario, List<MensajeHistorialDto> historial,
                                             String fileName, String fileMime, String fileData) {
        List<Content> messages = new ArrayList<>();

        if (historial != null) {
            for (MensajeHistorialDto msg : historial) {
                String role = "user".equals(msg.rol) ? "user" : "model";
                messages.add(Content.builder()
                        .role(role)
                        .parts(Part.fromText(msg.contenido == null ? "" : msg.contenido))
                        .build());
            }
        }

        List<Part> partes = new ArrayList<>();
        String textoDocExtraido = "";

        if (fileData != null && !fileData.isBlank() && fileMime != null && !fileMime.isBlank()) {
            try {
                byte[] fileBytes = Base64.getDecoder().decode(fileData);
                String mimeLower = fileMime.toLowerCase();
                String nombreLower = fileName == null ? "" : fileName.toLowerCase();

                if (mimeLower.contains("pdf")) {
                    LOG.infof("Procesando PDF nativo (%d bytes)", fileBytes.length);
                    partes.add(Part.fromBytes(fileBytes, "application/pdf"));

                } else if (mimeLower.contains("wordprocessingml") || mimeLower.contains("msword")
                        || nombreLower.endsWith(".docx")) {
                    LOG.infof("Procesando DOCX: %s", fileName);
                    textoDocExtraido = documentExtractionService.extraerTextoDocx(fileBytes);

                } else if (mimeLower.contains("spreadsheetml") || mimeLower.contains("excel")
                        || nombreLower.endsWith(".xlsx")) {
                    LOG.infof("Procesando XLSX: %s", fileName);
                    textoDocExtraido = documentExtractionService.extraerTextoXlsx(fileBytes);

                } else if (mimeLower.contains("csv") || nombreLower.endsWith(".csv")) {
                    LOG.infof("Procesando CSV: %s", fileName);
                    textoDocExtraido = truncar(new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8), 8000);

                } else if (mimeLower.contains("text") || nombreLower.endsWith(".txt")) {
                    LOG.infof("Procesando TXT: %s", fileName);
                    textoDocExtraido = truncar(new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8), 8000);

                } else {
                    LOG.warnf("Tipo MIME no reconocido (%s), intentando como bytes inline", fileMime);
                    partes.add(Part.fromBytes(fileBytes, fileMime));
                }
            } catch (Exception e) {
                LOG.errorf("Error procesando archivo adjunto: %s", e.getMessage());
                textoDocExtraido = "(Error al procesar el archivo: " + e.getMessage() + ")";
            }
        }

        String textoMensajeFinal = mensajeUsuario;
        if (textoDocExtraido != null && !textoDocExtraido.isEmpty()) {
            String nombreArchivo = (fileName == null || fileName.isBlank()) ? "documento adjunto" : fileName;
            String separador = "=".repeat(50);
            textoMensajeFinal = "[DOCUMENTO ADJUNTO: " + nombreArchivo + "]\n"
                    + separador + "\n"
                    + truncar(textoDocExtraido, 6000) + "\n"
                    + separador + "\n\n"
                    + "Consulta del usuario sobre este documento:\n" + mensajeUsuario;
        }

        partes.add(Part.fromText(textoMensajeFinal));
        messages.add(Content.builder().role("user").parts(partes).build());
        return messages;
    }

    // ===================== SUGERENCIAS =====================

    List<String> extraerSugerencias(String respuesta) {
        try {
            Matcher m = SUGERENCIAS_PATTERN.matcher(respuesta);
            String ultimoMatch = null;
            while (m.find()) {
                ultimoMatch = m.group();
            }
            if (ultimoMatch != null) {
                var node = JSON.readTree(ultimoMatch);
                List<String> sugs = new ArrayList<>();
                node.path("sugerencias").forEach(n -> sugs.add(n.asText()));
                List<String> combinado = new ArrayList<>(sugs);
                combinado.addAll(SUGERENCIAS_DEFECTO);
                return combinado.subList(0, Math.min(3, combinado.size()));
            }
        } catch (Exception ignored) {
            // Igual que Python: cualquier fallo de parseo cae al default silenciosamente.
        }
        return SUGERENCIAS_DEFECTO;
    }

    String limpiarRespuesta(String respuesta) {
        return SUGERENCIAS_PATTERN.matcher(respuesta).replaceAll("").strip();
    }

    // ===================== CATEGORIZACIÓN (para FAQ_Log / consulta_log) =====================

    public String categorizarPregunta(String pregunta) {
        String p = pregunta == null ? "" : pregunta.toLowerCase();
        for (var entry : CATEGORIAS.entrySet()) {
            for (String palabra : entry.getValue()) {
                if (p.contains(palabra)) {
                    return entry.getKey();
                }
            }
        }
        return "general";
    }

    // ===================== UTILIDADES =====================

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String truncar(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ===================== GENERACIÓN ESTRUCTURADA (módulo de estudio) =====================

    /**
     * Pide a Gemini una respuesta en JSON puro y la devuelve parseada. A diferencia de
     * {@link #generarRespuesta}, acá NO se declaran tools ni se extraen sugerencias: es
     * una llamada de un solo turno para generar datos (ruta de estudio, cuestionario).
     *
     * Usa {@code responseMimeType("application/json")} para que el modelo no envuelva la
     * salida en ```json ... ``` — igual se limpia por las dudas, porque algunos modelos
     * del fallback lo siguen haciendo.
     *
     * Recorre la MISMA cadena de modelos que el chat, con idéntica clasificación de
     * errores, para no tener dos comportamientos distintos ante cuota agotada.
     */
    public JsonNode generarJson(String systemPrompt, String userPrompt) {
        String ultimoError = null;

        for (String modelo : modelosFallback()) {
            try {
                GenerateContentResponse response = client().models.generateContent(
                        modelo,
                        Content.fromParts(Part.fromText(userPrompt)),
                        GenerateContentConfig.builder()
                                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                                .temperature(0.4f)
                                .maxOutputTokens(8192)
                                .responseMimeType("application/json")
                                .build());

                String texto = response.text() == null ? "" : response.text().strip();
                return JSON.readTree(limpiarCercaJson(texto));

            } catch (Exception e) {
                ultimoError = String.valueOf(e.getMessage());
                String err = ultimoError.toLowerCase();
                if (err.contains("429") || err.contains("quota") || err.contains("rate")) {
                    dormir(1000);
                }
                LOG.warnf("generarJson falló con '%s': %s", modelo, ultimoError);
            }
        }

        throw ApiException.serviceUnavailable(
                "La IA no pudo generar el contenido en este momento. Detalle: " + truncar(ultimoError, 150));
    }

    /** Quita el cercado ```json ... ``` si el modelo lo agregó pese a responseMimeType. */
    private static String limpiarCercaJson(String texto) {
        String t = texto.strip();
        if (t.startsWith("```")) {
            int primerSalto = t.indexOf('\n');
            if (primerSalto > 0) t = t.substring(primerSalto + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.strip();
    }
}
