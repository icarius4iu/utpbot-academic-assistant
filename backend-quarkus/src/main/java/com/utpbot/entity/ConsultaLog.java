package com.utpbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Equivalente a la hoja "FAQ_Log" (sheets_service.py: registrar_faq, obtener_faq_log;
 * analytics_service.py: todas las agregaciones). Arranca vacía en producción — por decisión
 * del usuario el historial de FAQ_Log NO se migra en el corte.
 */
@Entity
@Table(name = "consulta_log")
public class ConsultaLog extends BaseEntity {

    @Column(nullable = false)
    public OffsetDateTime fecha = OffsetDateTime.now();

    @Column(name = "codigo_usuario", nullable = false, length = 20)
    public String codigoUsuario;

    @Column(nullable = false, length = 15)
    public String rol;

    @Column(nullable = false, columnDefinition = "text")
    public String pregunta;

    @Column(nullable = false, length = 30)
    public String categoria = "general";
}
