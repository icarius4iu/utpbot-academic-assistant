package com.utpbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.utpbot.dto.estudio.SubirMaterialRequest;
import com.utpbot.entity.*;
import com.utpbot.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

/**
 * Módulo de estudio personalizado: materiales, rutas desde sílabo y cuestionarios.
 *
 * Todo el contenido generado se ancla al texto REAL del material del alumno (se le pasa
 * a la IA en el prompt), no al conocimiento general del modelo — es lo que hace que la
 * ruta y los cuestionarios sean "contextuados" al curso y no genéricos.
 */
@ApplicationScoped
public class EstudioService {

    private static final Logger LOG = Logger.getLogger(EstudioService.class);

    /**
     * Tope de texto que se le manda a la IA. Los sílabos rondan los 10-30k caracteres;
     * 40k deja margen sin acercarse al límite de tokens del modelo ni disparar el costo.
     * El texto completo igual queda guardado en la base.
     */
    private static final int MAX_CARACTERES_PROMPT = 40_000;

    @Inject
    DocumentExtractionService extractor;

    @Inject
    GeminiService gemini;

    // ===================== MATERIALES =====================

    @Transactional
    public MaterialEstudio subirMaterial(Estudiante estudiante, SubirMaterialRequest req) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(req.fileData);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("El archivo no llegó en base64 válido.");
        }
        if (bytes.length == 0) {
            throw ApiException.badRequest("El archivo está vacío.");
        }

        String texto = extractor.extraerTexto(bytes, req.nombreArchivo, req.mimeType);
        if (texto == null || texto.isBlank()) {
            throw ApiException.badRequest(
                    "No se pudo extraer texto de '" + req.nombreArchivo + "'. "
                    + "Formatos soportados: PDF, PPTX, DOCX, XLSX, TXT/CSV. "
                    + "Si es un PDF escaneado (imágenes), no tiene texto seleccionable.");
        }

        MaterialEstudio material = new MaterialEstudio();
        material.estudiante = estudiante;
        material.tipo = MaterialEstudio.TIPO_SILABO.equalsIgnoreCase(req.tipo)
                ? MaterialEstudio.TIPO_SILABO : MaterialEstudio.TIPO_MATERIAL;
        material.nombreArchivo = req.nombreArchivo;
        material.mimeType = req.mimeType;
        material.codigoCurso = req.codigoCurso;
        material.textoExtraido = texto;
        material.caracteres = texto.length();
        material.persist();

        LOG.infof("Material '%s' (%s) subido por %s: %d caracteres",
                material.nombreArchivo, material.tipo, estudiante.codigo, material.caracteres);
        return material;
    }

    @Transactional
    public void eliminarMaterial(Long materialId, Long estudianteId) {
        MaterialEstudio m = MaterialEstudio.findPropio(materialId, estudianteId);
        if (m == null) throw ApiException.notFound("Material no encontrado.");
        m.delete();
    }

    // ===================== HISTORIA 1: RUTA DE ESTUDIO DESDE EL SÍLABO =====================

    @Transactional
    public RutaEstudio generarRuta(Long materialId, Estudiante estudiante) {
        MaterialEstudio material = MaterialEstudio.findPropio(materialId, estudiante.id);
        if (material == null) throw ApiException.notFound("Material no encontrado.");

        String systemPrompt = """
                Eres UTP IA, asistente académico de la Universidad Tecnológica del Perú.
                Tu tarea es leer el SÍLABO de un curso y convertirlo en una ruta de estudio
                progresiva y realista para el estudiante.

                REGLAS:
                • Basate ÚNICAMENTE en el contenido del sílabo entregado. No inventes temas
                  que no aparezcan en él.
                • Ordená los temas de forma progresiva: lo que hay que saber primero va primero.
                • Estimá horas de estudio razonables por tema (entre 1 y 12 horas).
                • Entre 4 y 15 temas. Si el sílabo tiene más unidades, agrupá las relacionadas.
                • Escribí en español, sin emojis.

                Respondé EXCLUSIVAMENTE con este JSON:
                {
                  "curso": "nombre del curso según el sílabo",
                  "titulo": "título corto de la ruta",
                  "descripcion": "2-3 oraciones sobre cómo encarar el curso",
                  "temas": [
                    {
                      "titulo": "nombre del tema",
                      "descripcion": "qué se estudia y por qué importa (1-2 oraciones)",
                      "horas_estimadas": 4.5
                    }
                  ]
                }
                """;

        String userPrompt = "SÍLABO (archivo: " + material.nombreArchivo + ")\n"
                + "=".repeat(50) + "\n"
                + recortar(material.textoExtraido) + "\n"
                + "=".repeat(50);

        JsonNode json = gemini.generarJson(systemPrompt, userPrompt);

        JsonNode temasJson = json.path("temas");
        if (!temasJson.isArray() || temasJson.isEmpty()) {
            throw ApiException.serviceUnavailable(
                    "La IA no pudo identificar temas en ese archivo. ¿Seguro que es un sílabo?");
        }

        RutaEstudio ruta = new RutaEstudio();
        ruta.estudiante = estudiante;
        ruta.material = material;
        ruta.curso = textoOSustituto(json.path("curso").asText(null),
                material.codigoCurso != null ? material.codigoCurso : "Curso sin identificar");
        ruta.titulo = textoOSustituto(json.path("titulo").asText(null), "Ruta de estudio");
        ruta.descripcion = json.path("descripcion").asText(null);
        ruta.persist();

        short orden = 1;
        for (JsonNode t : temasJson) {
            TemaRuta tema = new TemaRuta();
            tema.ruta = ruta;
            tema.orden = orden++;
            tema.titulo = textoOSustituto(t.path("titulo").asText(null), "Tema " + (orden - 1));
            tema.descripcion = t.path("descripcion").asText(null);
            if (t.hasNonNull("horas_estimadas")) {
                tema.horasEstimadas = BigDecimal.valueOf(t.path("horas_estimadas").asDouble());
            }
            tema.persist();
        }

        LOG.infof("Ruta generada para %s: %s (%d temas)", estudiante.codigo, ruta.titulo, orden - 1);
        return ruta;
    }

    @Transactional
    public TemaRuta marcarTema(Long temaId, Long estudianteId, boolean completado) {
        TemaRuta tema = TemaRuta.findPropio(temaId, estudianteId);
        if (tema == null) throw ApiException.notFound("Tema no encontrado.");
        tema.completado = completado;
        tema.completadoAt = completado ? java.time.OffsetDateTime.now() : null;
        tema.persist();
        return tema;
    }

    // ===================== HISTORIA 2: CUESTIONARIOS Y RESÚMENES =====================

    @Transactional
    public Cuestionario generarCuestionario(Long materialId, Estudiante estudiante, int cantidadPreguntas) {
        MaterialEstudio material = MaterialEstudio.findPropio(materialId, estudiante.id);
        if (material == null) throw ApiException.notFound("Material no encontrado.");

        int cantidad = Math.max(3, Math.min(cantidadPreguntas, 20));

        String systemPrompt = """
                Eres UTP IA, asistente académico de la Universidad Tecnológica del Perú.
                Generá un cuestionario de autoevaluación a partir del material de estudio
                que te da el alumno.

                REGLAS:
                • Las preguntas deben responderse SOLO con el material entregado. No agregues
                  temas externos.
                • Cada pregunta tiene exactamente 4 opciones y una sola correcta.
                • Las opciones incorrectas deben ser plausibles, no absurdas.
                • Incluí una explicación breve de por qué la correcta lo es.
                • Variá la dificultad: algunas de recordar datos, otras de comprender conceptos.
                • Escribí en español, sin emojis.

                Respondé EXCLUSIVAMENTE con este JSON:
                {
                  "titulo": "título del cuestionario",
                  "preguntas": [
                    {
                      "enunciado": "texto de la pregunta",
                      "opciones": ["opción A", "opción B", "opción C", "opción D"],
                      "indice_correcto": 0,
                      "explicacion": "por qué esa es la correcta"
                    }
                  ]
                }
                """;

        String userPrompt = "Generá " + cantidad + " preguntas sobre este material.\n\n"
                + "MATERIAL (archivo: " + material.nombreArchivo + ")\n"
                + "=".repeat(50) + "\n"
                + recortar(material.textoExtraido) + "\n"
                + "=".repeat(50);

        JsonNode json = gemini.generarJson(systemPrompt, userPrompt);
        JsonNode preguntasJson = json.path("preguntas");
        if (!preguntasJson.isArray() || preguntasJson.isEmpty()) {
            throw ApiException.serviceUnavailable("La IA no pudo generar preguntas sobre ese material.");
        }

        Cuestionario cuestionario = new Cuestionario();
        cuestionario.estudiante = estudiante;
        cuestionario.material = material;
        cuestionario.titulo = textoOSustituto(json.path("titulo").asText(null),
                "Cuestionario sobre " + material.nombreArchivo);
        cuestionario.persist();

        short orden = 1;
        for (JsonNode p : preguntasJson) {
            JsonNode opcionesJson = p.path("opciones");
            if (!opcionesJson.isArray() || opcionesJson.size() < 2) {
                continue; // se descarta una pregunta malformada en vez de romper todo el cuestionario
            }
            StringBuilder opciones = new StringBuilder();
            for (int i = 0; i < opcionesJson.size(); i++) {
                if (i > 0) opciones.append("\n");
                opciones.append(opcionesJson.get(i).asText().replace("\n", " "));
            }

            int indice = p.path("indice_correcto").asInt(0);
            if (indice < 0 || indice >= opcionesJson.size()) indice = 0;

            PreguntaCuestionario pregunta = new PreguntaCuestionario();
            pregunta.cuestionario = cuestionario;
            pregunta.orden = orden++;
            pregunta.enunciado = p.path("enunciado").asText("Pregunta " + (orden - 1));
            pregunta.opciones = opciones.toString();
            pregunta.indiceCorrecto = (short) indice;
            pregunta.explicacion = p.path("explicacion").asText(null);
            pregunta.persist();
        }

        LOG.infof("Cuestionario generado para %s: %d preguntas", estudiante.codigo, orden - 1);
        return cuestionario;
    }

    @Transactional
    public ResumenMaterial generarResumen(Long materialId, Estudiante estudiante) {
        MaterialEstudio material = MaterialEstudio.findPropio(materialId, estudiante.id);
        if (material == null) throw ApiException.notFound("Material no encontrado.");

        String systemPrompt = """
                Eres UTP IA, asistente académico de la Universidad Tecnológica del Perú.
                Resumí el material de estudio del alumno de forma clara y útil para repasar.

                REGLAS:
                • Basate SOLO en el material entregado.
                • Estructurá el resumen con estos apartados, en este orden:
                  "Ideas principales", "Conceptos clave" y "Para repasar antes del examen".
                • Usá viñetas cortas. Sin emojis.
                • Escribí en español.

                Respondé EXCLUSIVAMENTE con este JSON:
                { "resumen": "el resumen completo en texto, con saltos de línea" }
                """;

        String userPrompt = "MATERIAL (archivo: " + material.nombreArchivo + ")\n"
                + "=".repeat(50) + "\n"
                + recortar(material.textoExtraido) + "\n"
                + "=".repeat(50);

        JsonNode json = gemini.generarJson(systemPrompt, userPrompt);
        String contenido = json.path("resumen").asText(null);
        if (contenido == null || contenido.isBlank()) {
            throw ApiException.serviceUnavailable("La IA no pudo generar el resumen.");
        }

        ResumenMaterial resumen = new ResumenMaterial();
        resumen.estudiante = estudiante;
        resumen.material = material;
        resumen.contenido = contenido;
        resumen.persist();
        return resumen;
    }

    // ===================== UTILIDADES =====================

    /** Recorta el texto que va al prompt avisando explícitamente que se truncó. */
    private static String recortar(String texto) {
        if (texto == null) return "";
        if (texto.length() <= MAX_CARACTERES_PROMPT) return texto;
        return texto.substring(0, MAX_CARACTERES_PROMPT)
                + "\n\n[... el documento continúa, se recortó por longitud ...]";
    }

    private static String textoOSustituto(String valor, String sustituto) {
        return (valor == null || valor.isBlank() || "null".equals(valor)) ? sustituto : valor;
    }
}
