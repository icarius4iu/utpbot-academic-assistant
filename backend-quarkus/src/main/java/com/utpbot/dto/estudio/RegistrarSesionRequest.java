package com.utpbot.dto.estudio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RegistrarSesionRequest {

    @NotNull
    @Min(value = 1, message = "La sesión debe tener al menos 1 minuto")
    @Max(value = 1440, message = "Una sesión no puede superar las 24 horas")
    public Short minutos;

    /** Tema de la ruta que se estudió (opcional). */
    public Long temaId;

    public String nota;
}
