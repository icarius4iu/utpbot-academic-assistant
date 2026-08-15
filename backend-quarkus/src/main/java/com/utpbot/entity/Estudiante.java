package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Equivalente a la hoja "Estudiantes" de Google Sheets
 * (backend/services/sheets_service.py: buscar_estudiante, recopilar_datos_estudiante).
 */
@Entity
@Table(name = "estudiantes")
public class Estudiante extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    public String codigo;

    @Column(nullable = false, length = 200)
    public String nombre;

    @Column(nullable = false, length = 150)
    public String carrera;

    @Column(nullable = false, length = 5)
    public String ciclo;

    @Column(name = "idioma_preferido", nullable = false, length = 5)
    public String idiomaPreferido = "es";

    /** Bcrypt. Sembrado en el ETL como bcrypt(codigo) — ver AuthService. */
    @Column(name = "password_hash", nullable = false, length = 100)
    public String passwordHash;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();

    public static Estudiante findByCodigo(String codigo) {
        return find("codigo", codigo).firstResult();
    }
}
