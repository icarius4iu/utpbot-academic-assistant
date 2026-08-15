package com.utpbot.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Acceso conveniente al {@link UtpPrincipal} autenticado dentro de un resource JAX-RS.
 * Usado por ChatResource para corregir el bug de autorización actual, donde
 * routes/chat.py confía en codigo_usuario/rol del BODY en vez de cruzarlos contra el
 * token verificado (ver plan, sección "Autenticación", punto 7).
 */
@RequestScoped
public class CurrentUser {

    @Inject
    SecurityIdentity identity;

    public UtpPrincipal principal() {
        return (UtpPrincipal) identity.getPrincipal();
    }

    public String codigo() {
        return principal().getCodigo();
    }

    public String rol() {
        return principal().getRol();
    }

    public String nombre() {
        return principal().getNombre();
    }

    public String idioma() {
        return principal().getIdioma();
    }
}
