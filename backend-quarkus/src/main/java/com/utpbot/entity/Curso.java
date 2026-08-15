package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;

/** Equivalente a la hoja "Cursos" (sheets_service.py: obtener_cursos). */
@Entity
@Table(name = "cursos")
public class Curso extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @Column(name = "nombre_curso", nullable = false, length = 150)
    public String nombreCurso;

    /** Nullable: la API del portal UTP no expone créditos (solo el ETL desde Sheets los trae). */
    public Short creditos;

    @Column(nullable = false, length = 30)
    public String estado;

    // ─── Campos que aporta la extensión "UTPBot Sync" (ver V2__plugin_sync_fields.sql) ───

    /** "classNumber" del portal (ej. "31088") — clave natural para el upsert al re-sincronizar. */
    @Column(name = "codigo_curso", length = 20)
    public String codigoCurso;

    /** "Presencial" | "Virtual 24/7" | ... (mapeado desde el enum del portal: P/VT/V/R). */
    @Column(length = 20)
    public String modalidad;

    /** Porcentaje de avance del curso, 0-100. */
    public Short progreso;

    @Column(length = 200)
    public String docente;

    public static List<Curso> findByEstudiante(Long estudianteId) {
        return list("estudiante.id", estudianteId);
    }

    public static Curso findByEstudianteYCodigo(Long estudianteId, String codigoCurso) {
        return find("estudiante.id = ?1 and codigoCurso = ?2", estudianteId, codigoCurso).firstResult();
    }
}
