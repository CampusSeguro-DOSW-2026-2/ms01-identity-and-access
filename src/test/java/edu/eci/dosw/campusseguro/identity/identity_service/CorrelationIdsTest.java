package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * X-Correlation-ID (RNF-MS01-03, estandares de integracion 2.3 y 6.1): se conserva el valor recibido si
 * es valido y si falta o no es valido se genera uno nuevo
 */
class CorrelationIdsTest {

    /**
     * Un valor valido se conserva y se le quitan los espacios laterales
     */
    @Test
    void resolve_validValue_isKeptWithoutSurroundingSpaces() {
        assertEquals("c19b-472d-a12b", CorrelationIds.resolve("  c19b-472d-a12b  "));
    }

    /**
     * Un UUID recibido se conserva tal cual
     */
    @Test
    void resolve_uuid_isKept() {
        String uuid = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        assertEquals(uuid, CorrelationIds.resolve(uuid));
    }

    /**
     * Un valor de exactamente 100 caracteres imprimibles todavia es valido
     */
    @Test
    void resolve_exactlyOneHundredCharacters_isKept() {
        String value = "a".repeat(100);

        assertEquals(value, CorrelationIds.resolve(value));
    }

    /**
     * Si la cabecera falta se genera un UUID
     */
    @Test
    void resolve_null_generatesAUuid() {
        String generated = CorrelationIds.resolve(null);

        assertDoesNotThrow(() -> UUID.fromString(generated));
    }

    /**
     * Un valor en blanco se trata como si faltara
     */
    @Test
    void resolve_blank_generatesAUuid() {
        String generated = CorrelationIds.resolve("   ");

        assertDoesNotThrow(() -> UUID.fromString(generated));
    }

    /**
     * Un valor con espacios internos no es valido y se reemplaza
     */
    @Test
    void resolve_valueWithInnerSpaces_isReplaced() {
        String generated = CorrelationIds.resolve("abc def");

        assertDoesNotThrow(() -> UUID.fromString(generated));
    }

    /**
     * Un valor con saltos de linea no es valido y se reemplaza, asi no se inyectan lineas en los logs
     */
    @Test
    void resolve_valueWithLineBreak_isReplaced() {
        String generated = CorrelationIds.resolve("abc\r\nFORGED");

        assertDoesNotThrow(() -> UUID.fromString(generated));
    }

    /**
     * Un valor de mas de 100 caracteres no es valido y se reemplaza
     */
    @Test
    void resolve_valueLongerThanOneHundred_isReplaced() {
        String generated = CorrelationIds.resolve("a".repeat(101));

        assertDoesNotThrow(() -> UUID.fromString(generated));
    }

    /**
     * Dos peticiones sin cabecera reciben valores distintos
     */
    @Test
    void resolve_twoCallsWithoutValue_generateDifferentValues() {
        assertNotEquals(CorrelationIds.resolve(null), CorrelationIds.resolve(null));
    }
}
