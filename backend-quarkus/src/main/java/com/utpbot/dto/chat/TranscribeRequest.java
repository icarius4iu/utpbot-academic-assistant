package com.utpbot.dto.chat;

import jakarta.validation.constraints.NotBlank;

/** Equivalente a routes/transcribe.py: TranscribeRequest{audio_base64, mime_type}. */
public class TranscribeRequest {

    @NotBlank
    public String audioBase64;

    public String mimeType = "audio/webm";
}
