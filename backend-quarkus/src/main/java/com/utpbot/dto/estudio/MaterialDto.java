package com.utpbot.dto.estudio;

import com.utpbot.entity.MaterialEstudio;

public class MaterialDto {
    public Long id;
    public String tipo;
    public String nombreArchivo;
    public String codigoCurso;
    public Integer caracteres;
    public String createdAt;
    /** Primeras líneas del texto extraído, para que el alumno confirme que se leyó bien. */
    public String vistaPrevia;

    public static MaterialDto de(MaterialEstudio m) {
        MaterialDto d = new MaterialDto();
        d.id = m.id;
        d.tipo = m.tipo;
        d.nombreArchivo = m.nombreArchivo;
        d.codigoCurso = m.codigoCurso;
        d.caracteres = m.caracteres;
        d.createdAt = m.createdAt.toString();
        String texto = m.textoExtraido == null ? "" : m.textoExtraido;
        d.vistaPrevia = texto.length() > 280 ? texto.substring(0, 280) + "…" : texto;
        return d;
    }
}
