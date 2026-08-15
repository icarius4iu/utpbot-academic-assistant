package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Equivalente a la hoja "Examenes" (sheets_service.py: obtener_examenes). */
@Entity
@Table(name = "examenes")
public class Examen extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @Column(nullable = false, length = 150)
    public String curso;

    @Column(nullable = false, length = 30)
    public String tipo;

    @Column(nullable = false)
    public LocalDate fecha;

    public LocalTime hora;

    @Column(length = 20)
    public String aula;

    public static List<Examen> findByEstudiante(Long estudianteId) {
        return list("estudiante.id", estudianteId);
    }
}
