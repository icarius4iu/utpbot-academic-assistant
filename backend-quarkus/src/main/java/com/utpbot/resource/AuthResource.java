package com.utpbot.resource;

import com.utpbot.dto.auth.LoginRequest;
import com.utpbot.dto.auth.LoginResponse;
import com.utpbot.service.AuthService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Equivalente a routes/auth.py — mismo path raíz "/auth" que el frontend ya tiene hardcodeado. */
@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public LoginResponse login(@Valid LoginRequest request) {
        return authService.login(request.codigo, request.password);
    }
}
