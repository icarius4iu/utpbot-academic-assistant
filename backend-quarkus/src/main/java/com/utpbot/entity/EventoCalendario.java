package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;

/**
 * Equivalente a la hoja "Calendario" (sheets_service.py: obtener_calendario).
 * {@code aplicaA} ∈ {"todos", "estudiantes", "docentes"} — mismo filtro que en Python.
 */
@Entity
@Table(name = "eventos_calendario")
public class EventoCalendario extends BaseEntity {

    @Column(nullable = false)
    public LocalDate fecha;

    @Column(nullable = false, length = 200)
    public String evento;

    @Column(columnDefinition = "text")
    public String descripcion;

    @Column(name = "aplica_a", nullable = false, length = 15)
    public String aplicaA;

    public static List<EventoCalendario> findAplicaA(String aplicaA) {
        return list("aplicaA = ?1 or aplicaA = 'todos'", aplicaA);
    }
}
