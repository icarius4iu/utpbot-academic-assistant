package com.utpbot.dto.estudio;

/** Meta diaria + estado de la racha. Respuesta de GET /estudio/racha y /estudio/meta. */
public class MetaEstudioDto {
    public Short minutosDiarios;
    public int minutosHoy;
    public boolean metaCumplidaHoy;
    /** Días consecutivos cumpliendo la meta (incluye hoy si ya se cumplió). */
    public int rachaActual;
    /** Racha más larga alcanzada históricamente. */
    public int mejorRacha;
    public int minutosSemana;
    /** Últimos 7 días: fecha ISO → minutos, para pintar el calendario de la racha. */
    public java.util.List<DiaDto> ultimos7Dias = new java.util.ArrayList<>();

    public static class DiaDto {
        public String fecha;
        public int minutos;
        public boolean cumplida;

        public DiaDto(String fecha, int minutos, boolean cumplida) {
            this.fecha = fecha;
            this.minutos = minutos;
            this.cumplida = cumplida;
        }
    }
}
