package com.utpbot.dto.admin;

import java.util.List;

public class FaqAnalyticsResponseDto {
    public long totalConsultas;
    public List<FaqCategoryDto> categorias;

    public FaqAnalyticsResponseDto(long totalConsultas, List<FaqCategoryDto> categorias) {
        this.totalConsultas = totalConsultas;
        this.categorias = categorias;
    }

    public static class FaqCategoryDto {
        public String categoria;
        public long cantidad;
        public List<String> preguntasEjemplo;

        public FaqCategoryDto(String categoria, long cantidad, List<String> preguntasEjemplo) {
            this.categoria = categoria;
            this.cantidad = cantidad;
            this.preguntasEjemplo = preguntasEjemplo;
        }
    }
}
