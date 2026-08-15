package com.utpbot.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Equivalente al bloque de prompt_builder.py que lee utp_info.txt (992 líneas / ~40KB)
 * del disco EN CADA request de chat. Aquí se carga UNA sola vez al arrancar y se
 * cachea en memoria — corrige ese I/O redundante (ver plan de migración, sección
 * "Integración con Gemini (Java)").
 *
 * El archivo se empaqueta como recurso de classpath: src/main/resources/knowledge/utp_info.txt
 * (copia exacta del utp_info.txt del repo Python, sin modificar).
 */
@ApplicationScoped
public class KnowledgeBaseService {

    private static final Logger LOG = Logger.getLogger(KnowledgeBaseService.class);
    private static final String RESOURCE_PATH = "/knowledge/utp_info.txt";
    private static final String NO_DISPONIBLE =
            "(La base de conocimiento UTP 2026 no está disponible en este momento).";

    private String contenido;

    void onStart(@Observes StartupEvent ev) {
        try (InputStream in = getClass().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOG.error("No se encontró " + RESOURCE_PATH + " en el classpath.");
                contenido = NO_DISPONIBLE;
                return;
            }
            contenido = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            LOG.infof("Base de conocimiento UTP cargada (%d caracteres).", contenido.length());
        } catch (IOException e) {
            LOG.error("Error leyendo la base de conocimiento UTP", e);
            contenido = NO_DISPONIBLE;
        }
    }

    /** Texto completo de utp_info.txt, listo para inyectar en el prompt de sistema. */
    public String contenido() {
        return contenido != null ? contenido : NO_DISPONIBLE;
    }
}
