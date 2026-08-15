package com.utpbot.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test plano (sin @QuarkusTest) de la lógica pura de GeminiService: categorización
 * (idéntica a categorizar_pregunta en Python) y extracción/limpieza del bloque JSON de
 * sugerencias. No requiere API key ni red — no toca generarRespuesta().
 */
class GeminiServiceTest {

    private final GeminiService service = new GeminiService();

    @Test
    void categorizaPorPrimerMatchIgualQuePython() {
        assertEquals("horarios", service.categorizarPregunta("¿Cuál es mi horario de clases?"));
        assertEquals("notas", service.categorizarPregunta("¿Cuál es mi nota final?"));
        assertEquals("examenes", service.categorizarPregunta("¿Cuándo es mi próximo examen parcial?"));
        assertEquals("asistencia", service.categorizarPregunta("¿Tengo alguna falta registrada?"));
        assertEquals("docente", service.categorizarPregunta("¿Quién es mi profesor de esta sección?"));
        assertEquals("utp_info", service.categorizarPregunta("¿Dónde queda el campus de la UTP?"));
        assertEquals("general", service.categorizarPregunta("Hola, ¿cómo estás?"));
    }

    @Test
    void categorizacionEsCaseInsensitive() {
        assertEquals("examenes", service.categorizarPregunta("¿CUÁNDO ES MI EXAMEN FINAL?"));
    }

    @Test
    void extraeYLimpiaSugerenciasDelBloqueJsonFinal() {
        String respuesta = "Tu horario es los lunes de 8 a 10am.\n\n"
                + "{\"sugerencias\": [\"¿Y los martes?\", \"¿Qué aula es?\", \"¿Quién dicta el curso?\"]}";

        List<String> sugerencias = service.extraerSugerencias(respuesta);
        assertEquals(List.of("¿Y los martes?", "¿Qué aula es?", "¿Quién dicta el curso?"), sugerencias);

        String limpio = service.limpiarRespuesta(respuesta);
        assertFalse(limpio.contains("sugerencias"));
        assertTrue(limpio.contains("Tu horario es los lunes de 8 a 10am."));
    }

    @Test
    void sinBloqueJsonCaeAlDefaultConTildes() {
        List<String> sugerencias = service.extraerSugerencias("Respuesta simple sin bloque de sugerencias.");
        assertEquals(List.of("¿Cuál es mi horario?", "¿Cuáles son mis notas?", "¿Cuándo es mi próximo examen?"), sugerencias);
    }

    @Test
    void bloqueJsonMalformadoCaeAlDefaultSinLanzar() {
        String respuesta = "Respuesta.\n{\"sugerencias\": [\"solo una\", ";
        List<String> sugerencias = service.extraerSugerencias(respuesta);
        assertEquals(List.of("¿Cuál es mi horario?", "¿Cuáles son mis notas?", "¿Cuándo es mi próximo examen?"), sugerencias);
    }
}
