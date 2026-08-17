package com.utpbot.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Un tema dentro de la ruta de estudio, con su orden y estado de avance. */
@Entity
@Table(name = "tema_ruta")
public class TemaRuta extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id", nullable = false)
    public RutaEstudio ruta;

    @Column(nullable = false)
    public Short orden;

    @Column(nullable = false, length = 200)
    public String titulo;

    @Column(columnDefinition = "text")
    public String descripcion;

    @Column(name = "horas_estimadas")
    public BigDecimal horasEstimadas;

    @Column(nullable = false)
    public Boolean completado = false;

    @Column(name = "completado_at")
    public OffsetDateTime completadoAt;

    public static List<TemaRuta> findByRuta(Long rutaId) {
        return list("ruta.id = ?1 order by orden", rutaId);
    }

    /** Valida propiedad navegando por la ruta, para que nadie marque temas ajenos. */
    public static TemaRuta findPropio(Long id, Long estudianteId) {
        return find("id = ?1 and ruta.estudiante.id = ?2", id, estudianteId).firstResult();
    }
}
