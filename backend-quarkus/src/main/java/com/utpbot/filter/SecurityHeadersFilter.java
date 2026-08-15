package com.utpbot.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;

/**
 * Equivalente a main.py: security_headers_middleware(). Cabeceras OWASP idénticas en
 * todas las respuestas, y Strict-Transport-Security solo fuera de modo debug — mismo
 * comportamiento condicional que hoy (`if os.getenv("DEBUG") != "true"`).
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class SecurityHeadersFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_TIME_PROPERTY = "utpbot.request.startNanos";

    @ConfigProperty(name = "app.debug")
    boolean debug;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(START_TIME_PROPERTY, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        var headers = responseContext.getHeaders();
        headers.putSingle("X-Content-Type-Options", "nosniff");
        headers.putSingle("X-Frame-Options", "DENY");
        headers.putSingle("X-XSS-Protection", "1; mode=block");
        headers.putSingle("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.putSingle("Permissions-Policy", "camera=(), geolocation=()");
        headers.putSingle("Cache-Control", "no-store, no-cache, must-revalidate");

        if (!debug) {
            headers.putSingle("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        Object startNanos = requestContext.getProperty(START_TIME_PROPERTY);
        if (startNanos instanceof Long start) {
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            headers.putSingle("X-Process-Time", Math.round(ms * 100.0) / 100.0 + "ms");
        }
    }
}
