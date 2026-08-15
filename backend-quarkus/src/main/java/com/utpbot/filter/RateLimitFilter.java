package com.utpbot.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import io.vertx.core.http.HttpServerRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Equivalente a main.py: slowapi Limiter(key_func=get_remote_address,
 * default_limits=["200/hour", "30/minute"]). Ambos límites se aplican POR IP,
 * simultáneamente, igual que hoy.
 *
 * Ventana deslizante en memoria — suficiente para una sola réplica de Railway (el
 * mismo supuesto de despliegue del backend Python actual). Si en el futuro se escala
 * a múltiples réplicas, esto debe moverse a un store compartido (Redis) — ver plan,
 * sección "Riesgos y decisiones abiertas".
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 200)
public class RateLimitFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "ratelimit.per-minute")
    int perMinute;

    @ConfigProperty(name = "ratelimit.per-hour")
    int perHour;

    private final Map<String, ConcurrentLinkedDeque<Long>> hitsPorIp = new ConcurrentHashMap<>();

    @Context
    HttpServerRequest vertxRequest;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (path.isEmpty() || path.equals("health")) {
            return; // healthcheck de Railway nunca debe ser limitado
        }

        String ip = clientIp();
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> deque = hitsPorIp.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());

        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > 3_600_000L) {
                deque.pollFirst();
            }

            long enUltimaHora = deque.size();
            long enUltimoMinuto = deque.stream().filter(t -> now - t <= 60_000L).count();

            if (enUltimaHora >= perHour || enUltimoMinuto >= perMinute) {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("detail", "Demasiadas solicitudes. Intenta de nuevo en unos minutos.");
                requestContext.abortWith(Response.status(429).entity(body).build());
                return;
            }

            deque.addLast(now);
        }
    }

    private String clientIp() {
        if (vertxRequest == null) {
            return "unknown";
        }
        String forwardedFor = vertxRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return vertxRequest.remoteAddress() != null ? vertxRequest.remoteAddress().host() : "unknown";
    }
}
