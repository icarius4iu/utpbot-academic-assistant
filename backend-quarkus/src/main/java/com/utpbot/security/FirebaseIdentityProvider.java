package com.utpbot.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Verifica el ID token de Firebase (emitido tras canjear el custom token que
 * AuthResource mintea en /auth/login) y construye la SecurityIdentity con el rol
 * tomado de los custom claims — ver plan de migración, sección "Autenticación:
 * Firebase Auth con custom tokens", punto 6.
 *
 * verifyIdToken() puede hacer una llamada de red bloqueante la primera vez que
 * necesita refrescar las claves públicas de Google (luego las cachea) — por eso se
 * ejecuta dentro de context.runBlocking(), tal como recomienda Quarkus para
 * IdentityProviders que hacen trabajo bloqueante.
 */
@ApplicationScoped
public class FirebaseIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {

    @Override
    public Class<TokenAuthenticationRequest> getRequestType() {
        return TokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TokenAuthenticationRequest request,
                                               AuthenticationRequestContext context) {
        return context.runBlocking(() -> verificar(request));
    }

    private SecurityIdentity verificar(TokenAuthenticationRequest request) {
        String idToken = request.getToken().getToken();
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
            Map<String, Object> claims = decoded.getClaims();

            String rol = asString(claims.get("rol"));
            String codigo = asString(claims.get("codigo"));
            String nombre = asString(claims.get("nombre"));
            String idioma = asString(claims.get("idioma"));

            if (rol == null || codigo == null) {
                // Token válido para Firebase pero sin los custom claims que este backend
                // requiere (ej. un token de otra app del mismo proyecto Firebase) — se
                // rechaza explícitamente en vez de dejar pasar un usuario sin rol.
                throw new AuthenticationFailedException("Token sin claims 'rol'/'codigo' esperados.");
            }

            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
            builder.setPrincipal(new UtpPrincipal(codigo, nombre, rol, idioma));
            builder.addRole(rol);
            builder.addCredential(new TokenCredential(idToken, "bearer"));
            return builder.build();

        } catch (FirebaseAuthException e) {
            // Token expirado/inválido/revocado — se traduce a 401 (no 403), tal como el
            // frontend espera para forzar logout (ver plan, sección "Autenticación").
            throw new AuthenticationFailedException(e);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
