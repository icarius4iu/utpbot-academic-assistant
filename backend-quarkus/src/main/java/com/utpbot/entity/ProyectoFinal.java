package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Equivalente a la hoja "Proyectos_Finales" (sheets_service.py: obtener_proyectos). */
@Entity
@Table(name = "proyectos_finales")
public class ProyectoFinal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @Column(nullable = false, length = 150)
    public String curso;

    @Column(nullable = false, length = 250)
    public String titulo;

    @Column(length = 30)
    public String grupo;

    @Column(name = "fecha_sustentacion")
    public LocalDate fechaSustentacion;

    public BigDecimal nota;

    public static List<ProyectoFinal> findByEstudiante(Long estudianteId) {
        return list("estudiante.id", estudianteId);
    }
}
