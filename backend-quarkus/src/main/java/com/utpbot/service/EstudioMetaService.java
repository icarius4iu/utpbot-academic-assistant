package com.utpbot.service;

import com.utpbot.dto.estudio.MetaEstudioDto;
import com.utpbot.dto.estudio.RegistrarSesionRequest;
import com.utpbot.entity.Estudiante;
import com.utpbot.entity.MetaEstudio;
import com.utpbot.entity.SesionEstudio;
import com.utpbot.entity.TemaRuta;
import com.utpbot.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Historia 3: meta diaria en minutos y racha de estudio.
 *
 * La racha se cuenta en días del CALENDARIO DE LIMA, no en UTC: si un alumno estudia a
 * las 23:30 hora peruana, eso cuenta para ese día y no para el siguiente. Por eso las
 * sesiones guardan la fecha local ya resuelta (ver V3__modulo_estudio.sql).
 */
@ApplicationScoped
public class EstudioMetaService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @Inject
    EntityManager em;

    private LocalDate hoyEnLima() {
        return LocalDate.now(ZONA_LIMA);
    }

    // ===================== META =====================

    @Transactional
    public MetaEstudio obtenerOCrearMeta(Estudiante estudiante) {
        MetaEstudio meta = MetaEstudio.findByEstudiante(estudiante.id);
        if (meta == null) {
            meta = new MetaEstudio();
            meta.estudiante = estudiante;
            meta.minutosDiarios = MetaEstudio.MINUTOS_POR_DEFECTO;
            meta.persist();
        }
        return meta;
    }

    @Transactional
    public MetaEstudio establecerMeta(Estudiante estudiante, Short minutosDiarios) {
        MetaEstudio meta = obtenerOCrearMeta(estudiante);
        meta.minutosDiarios = minutosDiarios;
        meta.updatedAt = OffsetDateTime.now();
        meta.persist();
        return meta;
    }

    // ===================== SESIONES =====================

    @Transactional
    public SesionEstudio registrarSesion(Estudiante estudiante, RegistrarSesionRequest req) {
        SesionEstudio sesion = new SesionEstudio();
        sesion.estudiante = estudiante;
        sesion.fecha = hoyEnLima();
        sesion.minutos = req.minutos;
        sesion.nota = req.nota;

        if (req.temaId != null) {
            TemaRuta tema = TemaRuta.findPropio(req.temaId, estudiante.id);
            if (tema == null) {
                throw ApiException.notFound("El tema indicado no existe o no es tuyo.");
            }
            sesion.tema = tema;
        }

        sesion.persist();
        return sesion;
    }

    // ===================== RACHA =====================

    /**
     * Racha = días consecutivos, hacia atrás, en los que la suma de minutos alcanzó la meta.
     *
     * Detalle importante: si HOY todavía no se cumplió la meta, la racha NO se corta —
     * se cuenta desde ayer. El día en curso recién "rompe" la racha cuando termina. De lo
     * contrario, todo alumno vería racha 0 cada mañana al despertarse.
     */
    @Transactional
    public MetaEstudioDto obtenerRacha(Estudiante estudiante) {
        MetaEstudio meta = obtenerOCrearMeta(estudiante);
        short objetivo = meta.minutosDiarios;
        LocalDate hoy = hoyEnLima();

        Map<LocalDate, Integer> minutosPorDia = minutosPorDia(estudiante.id, hoy.minusYears(1));

        MetaEstudioDto dto = new MetaEstudioDto();
        dto.minutosDiarios = objetivo;
        dto.minutosHoy = minutosPorDia.getOrDefault(hoy, 0);
        dto.metaCumplidaHoy = dto.minutosHoy >= objetivo;

        // Racha actual
        int racha = 0;
        LocalDate cursor = dto.metaCumplidaHoy ? hoy : hoy.minusDays(1);
        while (minutosPorDia.getOrDefault(cursor, 0) >= objetivo) {
            racha++;
            cursor = cursor.minusDays(1);
        }
        dto.rachaActual = racha;

        // Mejor racha histórica
        int mejor = 0, corriente = 0;
        LocalDate dia = hoy.minusYears(1);
        while (!dia.isAfter(hoy)) {
            if (minutosPorDia.getOrDefault(dia, 0) >= objetivo) {
                corriente++;
                mejor = Math.max(mejor, corriente);
            } else {
                corriente = 0;
            }
            dia = dia.plusDays(1);
        }
        dto.mejorRacha = Math.max(mejor, racha);

        // Últimos 7 días (para el calendario de la UI) y total semanal
        int semana = 0;
        for (int i = 6; i >= 0; i--) {
            LocalDate d = hoy.minusDays(i);
            int min = minutosPorDia.getOrDefault(d, 0);
            semana += min;
            dto.ultimos7Dias.add(new MetaEstudioDto.DiaDto(d.toString(), min, min >= objetivo));
        }
        dto.minutosSemana = semana;

        return dto;
    }

    /** Suma de minutos por día, agregada en SQL (no trae todas las sesiones a memoria). */
    private Map<LocalDate, Integer> minutosPorDia(Long estudianteId, LocalDate desde) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createQuery(
                        "SELECT s.fecha, SUM(s.minutos) FROM SesionEstudio s "
                        + "WHERE s.estudiante.id = :eid AND s.fecha >= :desde GROUP BY s.fecha")
                .setParameter("eid", estudianteId)
                .setParameter("desde", desde)
                .getResultList();

        Map<LocalDate, Integer> mapa = new HashMap<>();
        for (Object[] fila : filas) {
            mapa.put((LocalDate) fila[0], ((Number) fila[1]).intValue());
        }
        return mapa;
    }
}
