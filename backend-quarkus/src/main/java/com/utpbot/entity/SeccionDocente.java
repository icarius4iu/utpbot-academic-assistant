package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Equivalente a la hoja "Secciones_Docente" (sheets_service.py: obtener_secciones_docente,
 * obtener_datos_estudiantes_seccion). La columna {@code lista_estudiantes} (string JSON en
 * Sheets) se normaliza aquí como una relación M:N real vía la tabla de unión
 * {@code secciones_docente_estudiantes}.
 */
@Entity
@Table(name = "secciones_docente")
public class SeccionDocente extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_id", nullable = false)
    public Docente docente;

    @Column(nullable = false, length = 150)
    public String curso;

    @Column(nullable = false, length = 10)
    public String seccion;

    @Column(length = 150)
    public String horario;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "secciones_docente_estudiantes",
        joinColumns = @JoinColumn(name = "seccion_id"),
        inverseJoinColumns = @JoinColumn(name = "estudiante_id")
    )
    public List<Estudiante> estudiantes = new ArrayList<>();

    public static List<SeccionDocente> findByDocente(Long docenteId) {
        return list("docente.id", docenteId);
    }
}
