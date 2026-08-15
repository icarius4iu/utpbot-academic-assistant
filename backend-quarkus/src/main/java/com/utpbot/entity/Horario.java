package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.List;

/** Equivalente a la hoja "Horarios" (sheets_service.py: obtener_horarios). */
@Entity
@Table(name = "horarios")
public class Horario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @Column(nullable = false, length = 150)
    public String curso;

    @Column(nullable = false, length = 15)
    public String dia;

    @Column(name = "hora_inicio", nullable = false)
    public LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    public LocalTime horaFin;

    /** Nullable: el portal UTP expone el campo `classroom` pero hoy viene sin poblar. */
    @Column(length = 20)
    public String aula;

    /** Texto libre — ver nota en el plan (§ETL): no garantizado que empate con Docente.nombre. */
    @Column(name = "docente_nombre", length = 200)
    public String docenteNombre;

    // ─── Campos que aporta la extensión "UTPBot Sync" (ver V2__plugin_sync_fields.sql) ───

    /** Cruza el bloque con su curso sin depender del nombre (que el portal manda en MAYÚSCULAS). */
    @Column(name = "codigo_curso", length = 20)
    public String codigoCurso;

    @Column(length = 20)
    public String modalidad;

    public static List<Horario> findByEstudiante(Long estudianteId) {
        return list("estudiante.id", estudianteId);
    }

    public static Horario findBloque(Long estudianteId, String codigoCurso, String dia, LocalTime horaInicio) {
        return find("estudiante.id = ?1 and codigoCurso = ?2 and dia = ?3 and horaInicio = ?4",
                estudianteId, codigoCurso, dia, horaInicio).firstResult();
    }
}
