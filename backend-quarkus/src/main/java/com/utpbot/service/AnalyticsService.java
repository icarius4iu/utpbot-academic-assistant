package com.utpbot.service;

import com.utpbot.dto.admin.*;
import com.utpbot.entity.ConsultaLog;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Equivalente a services/analytics_service.py. A diferencia de Python (que lee TODA la
 * hoja FAQ_Log a memoria en cada llamada y la pliega ahí), aquí las agregaciones son
 * consultas SQL `GROUP BY` reales contra `consulta_log` — ver plan de migración,
 * sección "Base de datos (Supabase PostgreSQL)": "no se necesita vista materializada
 * al volumen esperado". Arranca vacía (no se migró el histórico de FAQ_Log, decisión
 * del usuario), así que todo esto empieza en cero hasta que entre tráfico real.
 */
@ApplicationScoped
public class AnalyticsService {

    private static final DateTimeFormatter FECHA_YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FECHA_LEGIBLE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    EntityManager em;

    @Transactional
    public OverviewStatsDto obtenerOverview() {
        return construirOverview();
    }

    @Transactional
    public List<DayStatDto> obtenerStatsPorDia(int dias) {
        return construirPorDia(dias);
    }

    @Transactional
    public List<CategoryStatDto> obtenerStatsPorCategoria() {
        return construirPorCategoria();
    }

    @Transactional
    public List<RoleStatDto> obtenerStatsPorRol() {
        return construirPorRol();
    }

    @Transactional
    public List<RecentLogDto> obtenerPreguntasRecientes(int limite) {
        return construirRecientes(limite, Integer.MAX_VALUE);
    }

    @Transactional
    public FaqAnalyticsResponseDto obtenerFaqAnalytics() {
        long total = ConsultaLog.count();
        List<CategoryStatDto> porCategoria = construirPorCategoria();
        List<FaqAnalyticsResponseDto.FaqCategoryDto> categorias = new ArrayList<>();
        for (CategoryStatDto c : porCategoria) {
            List<String> ejemplos = em.createQuery(
                            "SELECT l.pregunta FROM ConsultaLog l WHERE l.categoria = :cat ORDER BY l.fecha DESC",
                            String.class)
                    .setParameter("cat", c.categoria)
                    .setMaxResults(5)
                    .getResultList();
            categorias.add(new FaqAnalyticsResponseDto.FaqCategoryDto(c.categoria, c.cantidad, ejemplos));
        }
        return new FaqAnalyticsResponseDto(total, categorias);
    }

    /** Equivalente a obtener_dashboard_completo(): un solo recorrido lógico para las 5 secciones del panel. */
    @Transactional
    public DashboardResponseDto obtenerDashboardCompleto() {
        return new DashboardResponseDto(
                construirOverview(),
                construirPorDia(30),
                construirPorCategoria(),
                construirPorRol(),
                construirRecientes(20, 120));
    }

    // ===================== Construcción interna =====================

    private OverviewStatsDto construirOverview() {
        OverviewStatsDto dto = new OverviewStatsDto();
        dto.totalConsultas = ConsultaLog.count();

        OffsetDateTime inicioHoy = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime finHoy = inicioHoy.plusDays(1);
        dto.consultasHoy = ConsultaLog.count("fecha >= ?1 and fecha < ?2", inicioHoy, finHoy);

        Long usuariosActivos = em.createQuery(
                "SELECT COUNT(DISTINCT l.codigoUsuario) FROM ConsultaLog l", Long.class).getSingleResult();
        dto.usuariosActivos = usuariosActivos == null ? 0 : usuariosActivos;

        List<CategoryStatDto> porCategoria = construirPorCategoria();
        dto.categoriaTop = porCategoria.isEmpty() ? "—" : porCategoria.get(0).categoria;

        long totalParaPct = dto.totalConsultas == 0 ? 1 : dto.totalConsultas;
        long estudiantes = ConsultaLog.count("rol", "estudiante");
        long docentes = ConsultaLog.count("rol", "docente");
        dto.porcentajeEstudiantes = redondear1(100.0 * estudiantes / totalParaPct);
        dto.porcentajeDocentes = redondear1(100.0 * docentes / totalParaPct);

        return dto;
    }

    private List<DayStatDto> construirPorDia(int dias) {
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        LocalDate desde = hoy.minusDays(dias - 1L);
        OffsetDateTime desdeInicio = desde.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(
                        "SELECT CAST(fecha AS date) AS dia, COUNT(*) FROM consulta_log "
                                + "WHERE fecha >= :desde GROUP BY CAST(fecha AS date)")
                .setParameter("desde", desdeInicio)
                .getResultList();

        // Hibernate 7 mapea DATE nativo a java.time.LocalDate directamente (no
        // java.sql.Date) — verificado en vivo: con la tabla vacía este cast nunca se
        // ejecutaba (result set vacío), y solo se detectó al probar con filas reales.
        Map<LocalDate, Long> conteos = new HashMap<>();
        for (Object[] fila : filas) {
            LocalDate dia = (LocalDate) fila[0];
            conteos.put(dia, ((Number) fila[1]).longValue());
        }

        List<DayStatDto> resultado = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hoy); d = d.plusDays(1)) {
            resultado.add(new DayStatDto(d.format(FECHA_YMD), conteos.getOrDefault(d, 0L)));
        }
        return resultado;
    }

    private List<CategoryStatDto> construirPorCategoria() {
        long total = ConsultaLog.count();
        long totalParaPct = total == 0 ? 1 : total;

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createQuery(
                        "SELECT l.categoria, COUNT(l) FROM ConsultaLog l GROUP BY l.categoria ORDER BY COUNT(l) DESC")
                .getResultList();

        List<CategoryStatDto> resultado = new ArrayList<>();
        for (Object[] fila : filas) {
            String categoria = (String) fila[0];
            long cantidad = (Long) fila[1];
            resultado.add(new CategoryStatDto(categoria, cantidad, redondear1(100.0 * cantidad / totalParaPct)));
        }
        return resultado;
    }

    private List<RoleStatDto> construirPorRol() {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createQuery(
                        "SELECT l.rol, COUNT(l) FROM ConsultaLog l GROUP BY l.rol ORDER BY COUNT(l) DESC")
                .getResultList();

        List<RoleStatDto> resultado = new ArrayList<>();
        for (Object[] fila : filas) {
            String rol = fila[0] == null || ((String) fila[0]).isBlank() ? "desconocido" : (String) fila[0];
            resultado.add(new RoleStatDto(rol, (Long) fila[1]));
        }
        return resultado;
    }

    private List<RecentLogDto> construirRecientes(int limite, int truncarPreguntaA) {
        List<ConsultaLog> logs = ConsultaLog.find("ORDER BY fecha DESC").page(Page.ofSize(limite)).list();
        List<RecentLogDto> resultado = new ArrayList<>();
        for (ConsultaLog log : logs) {
            String pregunta = log.pregunta;
            if (pregunta != null && pregunta.length() > truncarPreguntaA) {
                pregunta = pregunta.substring(0, truncarPreguntaA);
            }
            resultado.add(new RecentLogDto(
                    log.fecha.format(FECHA_LEGIBLE), log.codigoUsuario, log.rol, log.categoria, pregunta));
        }
        return resultado;
    }

    private static double redondear1(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
