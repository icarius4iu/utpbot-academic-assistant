package com.utpbot.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Sesión de estudio registrada. La `fecha` es la fecha LOCAL de Lima, no UTC:
 * la racha se cuenta en días del calendario del alumno (ver EstudioMetaService).
 */
@Entity
@Table(name = "sesion_estudio")
public class SesionEstudio extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @Column(nullable = false)
    public LocalDate fecha;

    @Column(nullable = false)
    public Short minutos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tema_id")
    public TemaRuta tema;

    @Column(length = 255)
    public String nota;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    public static List<SesionEstudio> findDesde(Long estudianteId, LocalDate desde) {
        return list("estudiante.id = ?1 and fecha >= ?2 order by fecha desc", estudianteId, desde);
    }
}
