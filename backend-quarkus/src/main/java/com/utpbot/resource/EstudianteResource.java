package com.utpbot.resource;

import com.utpbot.dto.sync.SincronizarRequest;
import com.utpbot.dto.sync.SincronizarResponse;
import com.utpbot.security.CurrentUser;
import com.utpbot.service.EstudianteSyncService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Recibe los datos que la extensión de navegador "UTPBot Sync" extrae del Portal del
 * Estudiante UTP — ver etl/UTPBotSync_GuiaImplementacion.md.
 *
 * Solo rol "estudiante": cada alumno sincroniza SUS propios datos. El código con el que
 * se escribe sale del token verificado (CurrentUser), nunca del body — mismo criterio
 * que ya aplicamos en ChatResource y DocenteResource.
 */
@Path("/estudiante")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EstudianteResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    EstudianteSyncService syncService;

    @POST
    @Path("/sincronizar")
    @RolesAllowed("estudiante")
    public SincronizarResponse sincronizar(SincronizarRequest request) {
        return syncService.sincronizar(currentUser.codigo(), request);
    }
}
