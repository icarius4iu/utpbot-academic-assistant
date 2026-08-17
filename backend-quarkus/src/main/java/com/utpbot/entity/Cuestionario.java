package com.utpbot.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;

/** Cuestionario de opción múltiple generado por la IA desde un material. */
@Entity
@Table(name = "cuestionario")
public class Cuestionario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    public MaterialEstudio material;

    @Column(nullable = false, length = 200)
    public String titulo;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    public static List<Cuestionario> findByEstudiante(Long estudianteId) {
        return list("estudiante.id = ?1 order by createdAt desc", estudianteId);
    }

    public static Cuestionario findPropio(Long id, Long estudianteId) {
        return find("id = ?1 and estudiante.id = ?2", id, estudianteId).firstResult();
    }
}
