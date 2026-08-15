package com.utpbot.dto.chat;

import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

/**
 * Equivalente a models/schemas.py: ChatRequest.
 *
 * codigo_usuario/rol siguen viajando en el body por compatibilidad de contrato con el
 * frontend (no se cambia lo que script.js envía), pero YA NO se confían ciegamente:
 * ChatResource los cruza contra el CurrentUser autenticado — ver plan de migración,
 * sección "Autenticación", punto 7 (corrige el bug de autorización de routes/chat.py).
 */
public class ChatRequest {

    @NotBlank
    public String codigoUsuario;

    @NotBlank
    public String rol;

    @NotBlank
    public String mensaje;

    public List<MensajeHistorialDto> historial = new ArrayList<>();

    public String idiomaPreferido = "es";

    public String fileName;
    public String fileMime;
    public String fileData;
}
