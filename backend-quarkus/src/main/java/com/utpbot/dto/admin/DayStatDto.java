package com.utpbot.dto.admin;

/** {fecha: "YYYY-MM-DD", cantidad} — admin.js parte "fecha" por "-" para el chart de línea. */
public class DayStatDto {
    public String fecha;
    public long cantidad;

    public DayStatDto(String fecha, long cantidad) {
        this.fecha = fecha;
        this.cantidad = cantidad;
    }
}
