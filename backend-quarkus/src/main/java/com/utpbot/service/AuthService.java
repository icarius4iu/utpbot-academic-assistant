package com.utpbot.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.utpbot.dto.auth.LoginResponse;
import com.utpbot.entity.Docente;
import com.utpbot.entity.Estudiante;
import com.utpbot.exception.ApiException;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Puerta de entrada de autenticación — equivalente a routes/auth.py (login()) y
 * utils/jwt_utils.py (crear_token()), adaptado al esquema de Firebase Auth con custom
 * tokens (ver plan, sección "Autenticación: Firebase Auth con custom tokens").
 *
 * Los 3 roles (estudiante, docente, admin) pasan por el MISMO mecanismo de custom
 * token por consistencia arquitectónica, aunque admin siga siendo una cuenta única
 * de variables de entorno (no una fila en la tabla) — igual diseño que hoy.
 */
@ApplicationScoped
public class AuthService {

    @ConfigProperty(name = "admin.username")
    String adminUsername;

    @ConfigProperty(name = "admin.password-hash")
    Optional<String> adminPasswordHash;

    @Transactional
    public LoginResponse login(String codigo, String password) {
        codigo = codigo.strip();
        password = password.strip();

        if (codigo.equals(adminUsername)) {
            return loginAdmin(password);
        }

        Estudiante estudiante = Estudiante.findByCodigo(codigo);
        if (estudiante != null) {
            return loginEstudiante(estudiante, password);
        }

        Docente docente = Docente.findByCodigo(codigo);
        if (docente != null) {
            return loginDocente(docente, password);
        }

        throw ApiException.notFound("Código institucional no encontrado.");
    }

    private LoginResponse loginAdmin(String password) {
        if (adminPasswordHash.isEmpty() || adminPasswordHash.get().isBlank()) {
            throw ApiException.serviceUnavailable("Panel de administración no configurado. Contacta al equipo técnico.");
        }
        if (!coincide(password, adminPasswordHash.get())) {
            throw ApiException.unauthorized("Credenciales de administrador incorrectas.");
        }

        String token = mintCustomToken("admin_ADMIN", "ADMIN", "Administrador UTP", "admin", "es");
        return new LoginResponse(token, "Administrador UTP", "admin", "es", "ADMIN");
    }

    private LoginResponse loginEstudiante(Estudiante e, String password) {
        // Password placeholder: password == codigo (hasheado). Ver plan, sección "Autenticación", punto 8.
        if (!coincide(password, e.passwordHash)) {
            throw ApiException.unauthorized("Contraseña incorrecta.");
        }
        String idioma = e.idiomaPreferido == null || e.idiomaPreferido.isBlank() ? "es" : e.idiomaPreferido;
        String token = mintCustomToken("estudiante_" + e.codigo, e.codigo, e.nombre, "estudiante", idioma);
        return new LoginResponse(token, e.nombre, "estudiante", idioma, e.codigo);
    }

    private LoginResponse loginDocente(Docente d, String password) {
        if (!coincide(password, d.passwordHash)) {
            throw ApiException.unauthorized("Contraseña incorrecta.");
        }
        String idioma = d.idiomaPreferido == null || d.idiomaPreferido.isBlank() ? "es" : d.idiomaPreferido;
        String token = mintCustomToken("docente_" + d.codigo, d.codigo, d.nombre, "docente", idioma);
        return new LoginResponse(token, d.nombre, "docente", idioma, d.codigo);
    }

    /**
     * BcryptUtil.matches() (WildFly Elytron) SOLO reconoce el identificador de versión
     * "$2a$" y lanza una excepción ante "$2b$"/"$2y$"/"$2x$" — aunque el hash en sí es
     * idéntico (la distinción 2a/2b es una corrección histórica de un bug de conteo de
     * bytes en contraseñas &gt;255 bytes, irrelevante aquí). La librería `bcrypt` estándar
     * de Python (usada en etl/transform_and_load.py) genera "$2b$" por defecto — sin esta
     * normalización, NINGÚN usuario migrado por el ETL podría iniciar sesión. Verificado
     * en vivo contra un hash real generado con Python bcrypt antes de este fix.
     */
    private static boolean coincide(String password, String hashAlmacenado) {
        String normalizado = hashAlmacenado.startsWith("$2b$") || hashAlmacenado.startsWith("$2y$") || hashAlmacenado.startsWith("$2x$")
                ? "$2a$" + hashAlmacenado.substring(4)
                : hashAlmacenado;
        return BcryptUtil.matches(password, normalizado);
    }

    /**
     * Mintea un Firebase custom token con los custom claims que FirebaseIdentityProvider
     * luego lee del ID token verificado (rol, codigo, nombre, idioma).
     */
    private String mintCustomToken(String uid, String codigo, String nombre, String rol, String idioma) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("codigo", codigo);
        claims.put("nombre", nombre);
        claims.put("rol", rol);
        claims.put("idioma", idioma);
        try {
            return FirebaseAuth.getInstance().createCustomToken(uid, claims);
        } catch (FirebaseAuthException ex) {
            throw ApiException.internal("No se pudo generar el token de sesión. Intenta nuevamente.");
        }
    }
}
