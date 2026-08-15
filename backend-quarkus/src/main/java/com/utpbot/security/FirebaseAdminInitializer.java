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

    @ConfigProperty(name = "firebase.credentials.json")
    String credentialsJson;

    void onStart(@Observes StartupEvent ev) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        GoogleCredentials credentials;
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)));
            LOG.info("Firebase Admin SDK: credenciales cargadas desde GOOGLE_APPLICATION_CREDENTIALS_JSON.");
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
            LOG.info("Firebase Admin SDK: credenciales cargadas desde Application Default Credentials.");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp.initializeApp(options);
        LOG.info("Firebase Admin SDK inicializado.");
    }
}
