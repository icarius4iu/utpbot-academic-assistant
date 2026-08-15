package com.utpbot.dto.auth;

/**
 * Equivalente a models/schemas.py: LoginResponse{token, nombre, rol, idioma, codigo}.
 * "token" contiene el CUSTOM TOKEN de Firebase (no un ID token) — el frontend debe
 * canjearlo con signInWithCustomToken() antes de usarlo como Bearer (ver plan,
 * sección "Autenticación", puntos 3-4).
 */
public class LoginResponse {

    public String token;
    public String nombre;
    public String rol;
    public String idioma;
    public String codigo;

    public LoginResponse(String token, String nombre, String rol, String idioma, String codigo) {
        this.token = token;
        this.nombre = nombre;
        this.rol = rol;
        this.idioma = idioma;
        this.codigo = codigo;
    }
}
