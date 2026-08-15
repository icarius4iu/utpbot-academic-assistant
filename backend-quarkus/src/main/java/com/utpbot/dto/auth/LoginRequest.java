package com.utpbot.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Equivalente a models/schemas.py: LoginRequest{codigo, password}. */
public class LoginRequest {

    @NotBlank
    public String codigo;

    @NotBlank
    public String password;
}
