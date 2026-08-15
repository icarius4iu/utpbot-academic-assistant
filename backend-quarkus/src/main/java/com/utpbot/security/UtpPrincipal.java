package com.utpbot.security;

import java.security.Principal;

/**
 * Principal autenticado, construido a partir de los custom claims del ID token de
 * Firebase (rol, codigo, nombre, idioma) — ver plan de migración, sección "Autenticación:
 * Firebase Auth con custom tokens", punto 2 ("claims = {rol, codigo, nombre, idioma}").
 *
 * Equivale al payload decodificado del JWT propio que usaba jwt_utils.py
 * (crear_token: {codigo, nombre, rol, idioma, exp, iat}).
 */
public class UtpPrincipal implements Principal {

    private final String codigo;
    private final String nombre;
    private final String rol;
    private final String idioma;

    public UtpPrincipal(String codigo, String nombre, String rol, String idioma) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.rol = rol;
        this.idioma = idioma;
    }

    @Override
    public String getName() {
        return codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRol() {
        return rol;
    }

    public String getIdioma() {
        return idioma;
    }
}
