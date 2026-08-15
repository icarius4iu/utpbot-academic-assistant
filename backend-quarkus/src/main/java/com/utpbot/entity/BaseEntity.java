package com.utpbot.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Base compartida para todas las entidades del dominio. Usa GenerationType.IDENTITY
 * (mapea 1:1 al BIGSERIAL de V1__init_schema.sql — un id por INSERT, sin asunciones de
 * tamaño de bloque) en vez de la estrategia SEQUENCE "enhanced" que trae por defecto
 * {@code PanacheEntity}, la cual espera una secuencia llamada exactamente
 * "&lt;tabla&gt;_SEQ" con incrementos de 50 en 50 — no existe en el esquema Flyway
 * (BIGSERIAL crea su propia secuencia con OTRO nombre), y usarla habría producido
 * colisiones de clave primaria en cuanto Hibernate intentara reservar un bloque de 50
 * IDs que la secuencia real de Postgres no tiene.
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
}
