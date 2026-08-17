package com.utpbot.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;

/** Resumen generado por la IA sobre un material. */
@Entity
@Table(name = "resumen_material")
public class ResumenMaterial extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    public MaterialEstudio material;

    @Column(nullable = false, columnDefinition = "text")
    public String contenido;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    public static List<ResumenMaterial> findByEstudiante(Long estudianteId) {
        return list("estudiante.id = ?1 order by createdAt desc", estudianteId);
    }
}
