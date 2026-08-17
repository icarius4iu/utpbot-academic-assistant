package com.utpbot.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Sílabo o material de apoyo que el alumno sube. Se guarda el TEXTO EXTRAÍDO,
 * no el binario original (ver nota de diseño en V3__modulo_estudio.sql).
 */
@Entity
@Table(name = "material_estudio")
public class MaterialEstudio extends BaseEntity {

    public static final String TIPO_SILABO = "SILABO";
    public static final String TIPO_MATERIAL = "MATERIAL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estudiante_id", nullable = false)
    public Estudiante estudiante;

    @Column(name = "codigo_curso", length = 20)
    public String codigoCurso;

    @Column(nullable = false, length = 15)
    public String tipo;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    public String nombreArchivo;

    @Column(name = "mime_type", length = 120)
    public String mimeType;

    @Column(name = "texto_extraido", nullable = false, columnDefinition = "text")
    public String textoExtraido;

    @Column(nullable = false)
    public Integer caracteres = 0;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    public static List<MaterialEstudio> findByEstudiante(Long estudianteId) {
        return list("estudiante.id = ?1 order by createdAt desc", estudianteId);
    }

    /** Busca por id validando que pertenezca al estudiante (evita acceso cruzado). */
    public static MaterialEstudio findPropio(Long id, Long estudianteId) {
        return find("id = ?1 and estudiante.id = ?2", id, estudianteId).firstResult();
    }
}
