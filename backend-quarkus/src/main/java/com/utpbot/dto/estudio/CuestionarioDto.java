package com.utpbot.dto.estudio;

import com.utpbot.entity.Cuestionario;
import com.utpbot.entity.PreguntaCuestionario;

import java.util.ArrayList;
import java.util.List;

public class CuestionarioDto {
    public Long id;
    public String titulo;
    public String createdAt;
    public List<PreguntaDto> preguntas = new ArrayList<>();

    public static class PreguntaDto {
        public Long id;
        public Short orden;
        public String enunciado;
        public List<String> opciones;
        /**
         * Se incluyen respuesta y explicación porque el cuestionario es de
         * autoevaluación (el alumno estudia con él, no es un examen calificado).
         */
        public Short indiceCorrecto;
        public String explicacion;

        public static PreguntaDto de(PreguntaCuestionario p) {
            PreguntaDto d = new PreguntaDto();
            d.id = p.id;
            d.orden = p.orden;
            d.enunciado = p.enunciado;
            d.opciones = p.opcionesComoLista();
            d.indiceCorrecto = p.indiceCorrecto;
            d.explicacion = p.explicacion;
            return d;
        }
    }

    public static CuestionarioDto de(Cuestionario c, List<PreguntaCuestionario> preguntas) {
        CuestionarioDto d = new CuestionarioDto();
        d.id = c.id;
        d.titulo = c.titulo;
        d.createdAt = c.createdAt.toString();
        preguntas.forEach(p -> d.preguntas.add(PreguntaDto.de(p)));
        return d;
    }
}
