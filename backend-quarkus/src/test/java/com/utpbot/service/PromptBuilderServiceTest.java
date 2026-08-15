package com.utpbot.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test plano (sin @QuarkusTest, sin contexto CDI) — solo valida que el conteo de
 * placeholders %s del text block de construirPromptSistema() cuadra con los argumentos
 * pasados a .formatted(). Java NO valida esto en tiempo de compilación (a diferencia de
 * un f-string de Python), así que un desajuste solo se detecta en runtime — de ahí este
 * test de regresión.
 */
class PromptBuilderServiceTest {

    @Test
    void construyePromptSinLanzarExcepcionYConTodasLasSeccionesEsperadas() throws Exception {
        PromptBuilderService service = new PromptBuilderService();
        setField(service, "knowledgeBaseService", stubKnowledgeBase());

        Map<String, Object> datosUsuario = new LinkedHashMap<>();
        datosUsuario.put("carrera", "Ingeniería de Sistemas");
        datosUsuario.put("ciclo", "5");

        String prompt = service.construirPromptSistema(
                "estudiante", "Ana García López", datosUsuario, "es", true);

        // No lanzó MissingFormatArgumentException / IllegalFormatException — si el conteo
        // de %s no cuadrara con los argumentos, la línea anterior ya habría fallado.
        assertTrue(prompt.contains("Eres UTP IA"));
        assertTrue(prompt.contains("ROL: ESTUDIANTE"));
        assertTrue(prompt.contains("NOMBRE: Ana García López"));
        assertTrue(prompt.contains("CARRERA: Ingeniería de Sistemas"));
        assertTrue(prompt.contains("CICLO ACTUAL: 5"));
        assertTrue(prompt.contains("IDIOMA PREFERIDO: es"));
        assertTrue(prompt.contains("(BASE DE CONOCIMIENTO DE PRUEBA)"));
        assertTrue(prompt.contains("PRIMER MENSAJE"));
        assertTrue(prompt.contains("agendar_tiempo_estudio"));
        assertTrue(prompt.contains("\"sugerencias\""));
        assertFalse(prompt.contains("%s")); // ningún placeholder quedó sin sustituir
    }

    @Test
    void mensajesSiguientesNoUsaSaludoInicial() throws Exception {
        PromptBuilderService service = new PromptBuilderService();
        setField(service, "knowledgeBaseService", stubKnowledgeBase());

        String prompt = service.construirPromptSistema(
                "docente", "Dr. Roberto Flores", new LinkedHashMap<>(), "en", false);

        assertTrue(prompt.contains("MENSAJES SIGUIENTES"));
        assertTrue(prompt.contains("DEPARTAMENTO: No disponible"));
        assertFalse(prompt.contains("%s"));
    }

    private static KnowledgeBaseService stubKnowledgeBase() throws Exception {
        KnowledgeBaseService kb = new KnowledgeBaseService();
        setField(kb, "contenido", "(BASE DE CONOCIMIENTO DE PRUEBA)");
        return kb;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
