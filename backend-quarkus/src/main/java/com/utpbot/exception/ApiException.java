package com.utpbot.exception;

/**
 * Excepción de negocio con código HTTP explícito. ApiExceptionMapper la convierte en
 * {"detail": "&lt;mensaje&gt;"} — el mismo shape de error que FastAPI genera por defecto
 * (HTTPException), del cual el frontend depende literalmente (script.js lee
 * `data.detail` en el fallo de login — ver plan, sección "Autenticación", punto 9).
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String detail) {
        super(detail);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(400, detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(401, detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(403, detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(404, detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(409, detail);
    }

    public static ApiException serviceUnavailable(String detail) {
        return new ApiException(503, detail);
    }

    public static ApiException internal(String detail) {
        return new ApiException(500, detail);
    }
}
