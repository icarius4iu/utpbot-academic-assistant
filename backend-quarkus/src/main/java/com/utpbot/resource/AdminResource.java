package com.utpbot.resource;

import com.utpbot.dto.admin.*;
import com.utpbot.service.AnalyticsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Equivalente a routes/admin.py — mismo path raíz "/admin". Todos los endpoints
 * requieren rol admin (equivalente a verificar_admin de jwt_utils.py).
 *
 * NO se porta POST /admin/update-sheet (edición genérica de "cualquier celda de
 * cualquier hoja") — es un patrón de la era Sheets que en Postgres sería un riesgo de
 * inyección/autorización si se genericiza. Decisión explícita del plan de migración,
 * sección "Riesgos y decisiones abiertas": reemplazarlo por CRUD tipado por entidad
 * es trabajo futuro, no bloqueante (el frontend actual no lo usa).
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminResource {

    @Inject
    AnalyticsService analyticsService;

    @GET
    @Path("/dashboard")
    public DashboardResponseDto dashboard() {
        return analyticsService.obtenerDashboardCompleto();
    }

    @GET
    @Path("/stats/overview")
    public OverviewStatsDto overview() {
        return analyticsService.obtenerOverview();
    }

    @GET
    @Path("/stats/by-day")
    public List<DayStatDto> byDay(@QueryParam("dias") @DefaultValue("30") int dias) {
        return analyticsService.obtenerStatsPorDia(dias);
    }

    @GET
    @Path("/stats/by-category")
    public List<CategoryStatDto> byCategory() {
        return analyticsService.obtenerStatsPorCategoria();
    }

    @GET
    @Path("/stats/by-role")
    public List<RoleStatDto> byRole() {
        return analyticsService.obtenerStatsPorRol();
    }

    @GET
    @Path("/recent-logs")
    public List<RecentLogDto> recentLogs(@QueryParam("limite") @DefaultValue("20") int limite) {
        return analyticsService.obtenerPreguntasRecientes(limite);
    }

    @GET
    @Path("/faq-analytics")
    public FaqAnalyticsResponseDto faqAnalytics() {
        return analyticsService.obtenerFaqAnalytics();
    }
}
