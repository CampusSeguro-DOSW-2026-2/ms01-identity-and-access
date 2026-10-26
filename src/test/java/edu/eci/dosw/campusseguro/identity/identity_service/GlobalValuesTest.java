package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;

/**
 * Valores globales que fijan los estándares de integración (sección 1)
 */
class GlobalValuesTest {

    /**
     * El emisor y la audiencia del JWT son los valores globales de los estándares
     */
    @Test
    void jwtIssuerAndAudience_areTheOnesOfTheStandards() {
        assertEquals("ms01-identity-and-access", GlobalValues.JWT_ISSUER);
        assertEquals("campusseguro", GlobalValues.JWT_AUDIENCE);
    }

    /**
     * La vigencia del JWT es de 3600 segundos
     */
    @Test
    void jwtLifetime_is3600Seconds() {
        assertEquals(3600, GlobalValues.JWT_LIFETIME_SECONDS);
    }

    /**
     * El timeout del cliente HTTP en las llamadas internas es de 2000 milisegundos
     */
    @Test
    void internalClientTimeout_is2000Milliseconds() {
        assertEquals(2000, GlobalValues.INTERNAL_CLIENT_TIMEOUT_MS);
    }
}
