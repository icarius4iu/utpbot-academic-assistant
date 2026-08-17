package com.utpbot.entity;

import jakarta.persistence.*;
import java.util.Arrays;
import java.util.List;

/**
 * Pregunta de opción múltiple. Las opciones se guardan separadas por salto de línea
 * (ver V3__modulo_estudio.sql) para no depender de jsonb y mantener simple el mapeo.
 */
@Entity
@Table(name = "pregunta_cuestionario")
public class PreguntaCuestionario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuestionario_id", nullable = false)
    public Cuestionario cuestionario;

    @Column(nullable = false)
    public Short orden;

    @Column(nullable = false, columnDefinition = "text")
    public String enunciado;

    @Column(nullable = false, columnDefinition = "text")
    public String opciones;

    @Column(name = "indice_correcto", nullable = false)
    public Short indiceCorrecto;

    @Column(columnDefinition = "text")
    public String explicacion;

    public List<String> opcionesComoLista() {
        return opciones == null || opciones.isBlank()
                ? List.of()
                : Arrays.asList(opciones.split("\n"));
    }

    public static List<PreguntaCuestionario> findByCuestionario(Long cuestionarioId) {
        return list("cuestionario.id = ?1 order by orden", cuestionarioId);
    }
}
