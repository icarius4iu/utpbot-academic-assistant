package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Equivalente a la hoja "Docentes" de Google Sheets
 * (backend/services/sheets_service.py: buscar_docente, recopilar_datos_docente).
 */
@Entity
@Table(name = "docentes")
public class Docente extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    public String codigo;

    @Column(nullable = false, length = 200)
    public String nombre;

    @Column(nullable = false, length = 150)
    public String departamento;

    /**
     * Texto libre, tal como en Sheets. La fuente estructurada real de cursos/secciones
     * es {@link SeccionDocente}; este campo solo se usa para volcarlo en el prompt de IA
     * (prompt_builder.py: CURSOS ASIGNADOS: {cursos_asignados}).
     */
    @Column(name = "cursos_asignados", columnDefinition = "text")
    public String cursosAsignados;

    @Column(name = "idioma_preferido", nullable = false, length = 5)
    public String idiomaPreferido = "es";

    @Column(name = "password_hash", nullable = false, length = 100)
    public String passwordHash;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();

    public static Docente findByCodigo(String codigo) {
        return find("codigo", codigo).firstResult();
    }
}
