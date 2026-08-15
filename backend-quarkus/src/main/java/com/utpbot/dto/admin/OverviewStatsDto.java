package com.utpbot.dto.admin;

/** Equivalente a models/schemas.py: OverviewStats. Serializado como overview.* en /admin/dashboard. */
public class OverviewStatsDto {
    public long totalConsultas;
    public long consultasHoy;
    public long usuariosActivos;
    public String categoriaTop;
    public double porcentajeEstudiantes;
    public double porcentajeDocentes;
}
