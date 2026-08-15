package com.utpbot.service;

import java.util.List;

/** Resultado de GeminiService.generarRespuesta() — equivalente a la tupla (str, List[str]) de Python. */
public record GeminiResult(String respuesta, List<String> sugerencias) {
}
