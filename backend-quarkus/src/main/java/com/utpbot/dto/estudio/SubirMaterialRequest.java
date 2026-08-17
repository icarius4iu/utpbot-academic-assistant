package com.utpbot.dto.estudio;

import jakarta.validation.constraints.NotBlank;

/** Subida de un sílabo o material. El archivo llega en base64, igual que en /chat. */
public class SubirMaterialRequest {

    @NotBlank
    public String nombreArchivo;

    public String mimeType;

    /** Contenido del archivo en base64 puro (sin el prefijo data:...;base64,). */
    @NotBlank
    public String fileData;

    /** "SILABO" o "MATERIAL". Si viene vacío se asume MATERIAL. */
    public String tipo;

    /** Código del curso al que pertenece (ej. "31088"), opcional. */
    public String codigoCurso;
}
