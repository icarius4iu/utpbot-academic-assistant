package com.utpbot.dto.sync;

import java.util.ArrayList;
import java.util.List;

/** Resumen de lo que efectivamente se guardó, para que el plugin lo muestre al alumno. */
public class SincronizarResponse {

    public boolean ok = true;
    public String codigoEstudiante;
    public int cursosCreados;
    public int cursosActualizados;
    public int horariosCreados;
    public int horariosActualizados;

    /** Cosas que se ignoraron y el usuario debería saber (ej. bloques sin código de curso). */
    public List<String> avisos = new ArrayList<>();
}
