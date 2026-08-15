package com.utpbot.security;

import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.Set;

/**
 * Extrae el header "Authorization: Bearer &lt;idToken&gt;" y delega la verificación a
 * {@link FirebaseIdentityProvider}. Reemplaza al JwtAuthMechanism implícito de
 * smallrye-jwt que se habría usado si la Opción B (solo Hosting) hubiera sido la
 * elegida — ver plan, sección "Autenticación", punto 6.
 *
 * Sin token o con formato inválido → Uni.createFrom().nullItem() (no autenticado, no
 * un error) para que Quarkus continúe como anónimo y sea @RolesAllowed quien decida
 * 401 vs 403 según el endpoint.
 */
@ApplicationScoped
public class FirebaseAuthMechanism implements HttpAuthenticationMechanism {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return Uni.createFrom().nullItem();
        }

        String idToken = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (idToken.isEmpty()) {
            return Uni.createFrom().nullItem();
        }

        TokenAuthenticationRequest request = new TokenAuthenticationRequest(new TokenCredential(idToken, "bearer"));
        return identityProviderManager.authenticate(request);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        // 401 con WWW-Authenticate: Bearer — el frontend trata cualquier 401/403 como
        // "cerrar sesión" (ver plan, sección "Autenticación", punto 6).
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
    }

    @Override
    public Set<Class<? extends io.quarkus.security.identity.request.AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(TokenAuthenticationRequest.class);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(
                new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "bearer"));
    }
}
