package com.utpbot.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Meta diaria de estudio del alumno, en minutos. Una por estudiante. */
@Entity
@Table(name = "meta_estudio")
public class MetaEstudio extends BaseEntity {

    public static final short MINUTOS_POR_DEFECTO = 30;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false, unique = true)
    public Estudiante estudiante;

    @Column(name = "minutos_diarios", nullable = false)
    public Short minutosDiarios = MINUTOS_POR_DEFECTO;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();

    public static MetaEstudio findByEstudiante(Long estudianteId) {
        return find("estudiante.id", estudianteId).firstResult();
    }
}
