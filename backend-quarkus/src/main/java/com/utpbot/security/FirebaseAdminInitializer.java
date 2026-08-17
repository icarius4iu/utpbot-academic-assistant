package com.utpbot.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Inicializa Firebase Admin SDK una sola vez al arrancar la aplicación.
 *
 * Sigue el MISMO patrón de credenciales que backend/services/sheets_service.py y
 * backend/services/calendar_service.py: primero intenta la variable de entorno con el
 * JSON completo de la cuenta de servicio (GOOGLE_APPLICATION_CREDENTIALS_JSON, patrón
 * usado en Railway), y si no está definida cae a Application Default Credentials (útil
 * en desarrollo local con `gcloud auth application-default login`).
 */
@ApplicationScoped
public class FirebaseAdminInitializer {

    private static final Logger LOG = Logger.getLogger(FirebaseAdminInitializer.class);

    /**
     * Optional a propósito: SmallRye Config convierte un valor vacío a null y hace
     * FALLAR EL ARRANQUE si el campo es un String plano. Sin esto, desplegar sin
     * GOOGLE_APPLICATION_CREDENTIALS_JSON tira un error críptico de conversión en vez
     * de degradar a Application Default Credentials como se pretende abajo.
     */
    @ConfigProperty(name = "firebase.credentials.json")
    Optional<String> credentialsJson;

    void onStart(@Observes StartupEvent ev) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            GoogleCredentials credentials;
            if (credentialsJson.isPresent() && !credentialsJson.get().isBlank()) {
                credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.get().getBytes(StandardCharsets.UTF_8)));
                LOG.info("Firebase Admin SDK: credenciales cargadas desde GOOGLE_APPLICATION_CREDENTIALS_JSON.");
            } else {
                credentials = GoogleCredentials.getApplicationDefault();
                LOG.info("Firebase Admin SDK: credenciales cargadas desde Application Default Credentials.");
            }

            FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build());
            LOG.info("Firebase Admin SDK inicializado.");

        } catch (IOException | RuntimeException e) {
            // Se arranca IGUAL, en modo degradado: sin Firebase el login y todo endpoint
            // autenticado devolverán error, pero /health y el arranque funcionan. Esto
            // permite levantar la app para tests o diagnóstico, y hace que el problema
            // se vea como un WARN legible en vez de un crash-loop con stacktrace de
            // conversión de config.
            LOG.warnf("Firebase Admin SDK NO inicializado (%s). "
                    + "El login y los endpoints autenticados no funcionarán hasta configurar "
                    + "GOOGLE_APPLICATION_CREDENTIALS_JSON.", e.getMessage());
        }
    }
}
