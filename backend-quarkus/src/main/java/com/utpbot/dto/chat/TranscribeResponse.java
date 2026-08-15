package com.utpbot.dto.chat;

/** Equivalente a routes/transcribe.py: TranscribeResponse{texto, confianza=1.0}. */
public class TranscribeResponse {
    public String texto;
    public double confianza = 1.0;

    public TranscribeResponse(String texto) {
        this.texto = texto;
    }
}
