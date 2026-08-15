package com.utpbot.dto.chat;

import java.util.List;

/** Equivalente a models/schemas.py: ChatResponse{respuesta, sugerencias}. */
public class ChatResponse {

    public String respuesta;
    public List<String> sugerencias;

    public ChatResponse(String respuesta, List<String> sugerencias) {
        this.respuesta = respuesta;
        this.sugerencias = sugerencias;
    }
}
