package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;

/** Equivalente a la hoja "Asistencia" (sheets_service.py: obtener_asistencia). */
@Entity
@Table(name = "asistencia")
public class Asistencia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @Column(nullable = false, length = 150)
    public String curso;

    @Column(nullable = false)
    public LocalDate fecha;

    @Column(nullable = false, length = 20)
    public String estado;

    public static List<Asistencia> findByEstudiante(Long estudianteId) {
        return list("estudiante.id", estudianteId);
    }
}
