package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;

/** Equivalente a la hoja "Notas" (sheets_service.py: obtener_notas). */
@Entity
@Table(name = "notas")
public class Nota extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @Column(nullable = false, length = 150)
    public String curso;

    public BigDecimal parcial;

    /** Columna real "final" — no se puede usar como identificador Java, por eso el nombre difiere. */
    @Column(name = "final")
    public BigDecimal notaFinal;

    public BigDecimal promedio;

    public static List<Nota> findByEstudiante(Long estudianteId) {
        return list("estudiante.id", estudianteId);
    }
}
