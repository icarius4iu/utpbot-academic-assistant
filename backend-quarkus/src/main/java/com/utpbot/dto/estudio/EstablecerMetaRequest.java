package com.utpbot.dto.estudio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class EstablecerMetaRequest {

    @NotNull
    @Min(value = 5, message = "La meta mínima es 5 minutos diarios")
    @Max(value = 720, message = "La meta máxima es 12 horas diarias")
    public Short minutosDiarios;
}
