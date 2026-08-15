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

/** Equivalente a la hoja "Avances_Trabajo" (sheets_service.py: obtener_avances). */
@Entity
@Table(name = "avances_trabajo")
public class AvanceTrabajo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @Column(nullable = false, length = 150)
    public String curso;

    @Column(nullable = false, length = 150)
    public String entregable;

    @Column(name = "fecha_entrega")
    public LocalDate fechaEntrega;

    @Column(nullable = false, length = 20)
    public String estado;

    public BigDecimal nota;

    public static List<AvanceTrabajo> findByEstudiante(Long estudianteId) {
        return list("estudiante.id", estudianteId);
    }
}
