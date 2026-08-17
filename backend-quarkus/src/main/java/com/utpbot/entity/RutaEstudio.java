package com.utpbot.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;

/** Ruta de estudio generada por la IA a partir de un sílabo. */
@Entity
@Table(name = "ruta_estudio")
public class RutaEstudio extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    public MaterialEstudio material;

    @Column(nullable = false, length = 150)
    public String curso;

    @Column(nullable = false, length = 200)
    public String titulo;

    @Column(columnDefinition = "text")
    public String descripcion;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    public static List<RutaEstudio> findByEstudiante(Long estudianteId) {
        return list("estudiante.id = ?1 order by createdAt desc", estudianteId);
    }

    public static RutaEstudio findPropia(Long id, Long estudianteId) {
        return find("id = ?1 and estudiante.id = ?2", id, estudianteId).firstResult();
    }
}
