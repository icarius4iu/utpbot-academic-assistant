package com.utpbot.dto.admin;

/** Equivalente a una fila cruda de FAQ_Log — consumido por la tabla de logs recientes del admin. */
public class RecentLogDto {
    public String fecha;
    public String codigoUsuario;
    public String rol;
    public String categoria;
    public String pregunta;

    public RecentLogDto(String fecha, String codigoUsuario, String rol, String categoria, String pregunta) {
        this.fecha = fecha;
        this.codigoUsuario = codigoUsuario;
        this.rol = rol;
        this.categoria = categoria;
        this.pregunta = pregunta;
    }
}
