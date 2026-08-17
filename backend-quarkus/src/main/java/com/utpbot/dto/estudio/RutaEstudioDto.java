package com.utpbot.dto.estudio;

import com.utpbot.entity.RutaEstudio;
import com.utpbot.entity.TemaRuta;

import java.util.ArrayList;
import java.util.List;

public class RutaEstudioDto {
    public Long id;
    public String curso;
    public String titulo;
    public String descripcion;
    public String createdAt;
    public int totalTemas;
    public int temasCompletados;
    public List<TemaDto> temas = new ArrayList<>();

    public static class TemaDto {
        public Long id;
        public Short orden;
        public String titulo;
        public String descripcion;
        public Double horasEstimadas;
        public Boolean completado;

        public static TemaDto de(TemaRuta t) {
            TemaDto d = new TemaDto();
            d.id = t.id;
            d.orden = t.orden;
            d.titulo = t.titulo;
            d.descripcion = t.descripcion;
            d.horasEstimadas = t.horasEstimadas == null ? null : t.horasEstimadas.doubleValue();
            d.completado = t.completado;
            return d;
        }
    }

    public static RutaEstudioDto de(RutaEstudio r, List<TemaRuta> temas) {
        RutaEstudioDto d = new RutaEstudioDto();
        d.id = r.id;
        d.curso = r.curso;
        d.titulo = r.titulo;
        d.descripcion = r.descripcion;
        d.createdAt = r.createdAt.toString();
        d.totalTemas = temas.size();
        d.temasCompletados = (int) temas.stream().filter(t -> Boolean.TRUE.equals(t.completado)).count();
        temas.forEach(t -> d.temas.add(TemaDto.de(t)));
        return d;
    }
}
