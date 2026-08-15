package com.utpbot.dto.admin;

public class CategoryStatDto {
    public String categoria;
    public long cantidad;
    public double porcentaje;

    public CategoryStatDto(String categoria, long cantidad, double porcentaje) {
        this.categoria = categoria;
        this.cantidad = cantidad;
        this.porcentaje = porcentaje;
    }
}
