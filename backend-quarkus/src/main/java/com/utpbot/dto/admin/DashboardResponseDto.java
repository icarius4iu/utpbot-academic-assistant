package com.utpbot.dto.admin;

import java.util.List;

/**
 * Equivalente a la respuesta de GET /admin/dashboard: {overview, por_dia, por_categoria,
 * por_rol, recientes}. admin.js lee estos 5 campos EXACTOS (por el naming strategy
 * SNAKE_CASE global, porDia -> por_dia, etc. — ver application.properties).
 */
public class DashboardResponseDto {
    public OverviewStatsDto overview;
    public List<DayStatDto> porDia;
    public List<CategoryStatDto> porCategoria;
    public List<RoleStatDto> porRol;
    public List<RecentLogDto> recientes;

    public DashboardResponseDto(OverviewStatsDto overview, List<DayStatDto> porDia,
                                 List<CategoryStatDto> porCategoria, List<RoleStatDto> porRol,
                                 List<RecentLogDto> recientes) {
        this.overview = overview;
        this.porDia = porDia;
        this.porCategoria = porCategoria;
        this.porRol = porRol;
        this.recientes = recientes;
    }
}
