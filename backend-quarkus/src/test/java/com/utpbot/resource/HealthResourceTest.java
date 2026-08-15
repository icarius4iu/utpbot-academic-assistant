package com.utpbot.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Test de integración (@QuarkusTest arranca la app completa: Postgres real vía
 * Flyway, Firebase Admin SDK, etc.) — requiere un entorno configurado (ver
 * API_TESTING.md, "Requisitos para correr esto"). No corre en este sandbox sin
 * una base de datos real; eso es esperado para un @QuarkusTest, no un bug del código.
 */
@QuarkusTest
class HealthResourceTest {

    @Test
    void health_devuelveOkYVersion() {
        given()
                .when().get("/health")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.is("ok"))
                .body("service", org.hamcrest.Matchers.is("utpbot-api"));
    }

    @Test
    void raiz_devuelveInfoDelServicio() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body("nombre", org.hamcrest.Matchers.is("UTPBot API"));
    }
}
