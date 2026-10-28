package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * X-Correlation-ID (RNF-MS01-03, Estándares de Integración): el valor es un UUID v4, se conserva si es
 * válido y si falta o no es válido se genera uno nuevo
 */
class CorrelationIdsTest {

    private static final String VALID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    /**
     * Comprueba que un valor generado es un UUID v4
     *
     * @param generated valor que se verifica
     */
    private static void assertGeneratedUuidV4(String generated) {
        assertEquals(4, UUID.fromString(generated).version());
        assertEquals(36, generated.length());
    }

    /**
     * Un UUID v4 recibido se conserva tal cual
     */
    @Test
    void resolve_uuidV4_isKept() {
        assertEquals(VALID, CorrelationIds.resolve(VALID));
    }

    /**
     * Un UUID v4 válido se conserva sin los espacios laterales
     */
    @Test
    void resolve_validValueWithSurroundingSpaces_isKeptTrimmed() {
        assertEquals(VALID, CorrelationIds.resolve("  " + VALID + "  "));
    }

    /**
     * Un UUID v4 en mayúsculas se acepta
     */
    @Test
    void resolve_uppercaseUuidV4_isAccepted() {
        assertEquals(VALID.toUpperCase(), CorrelationIds.resolve(VALID.toUpperCase()));
    }

    /**
     * Un UUID de otra versión no es un UUID v4 y se reemplaza
     */
    @Test
    void resolve_uuidOfAnotherVersion_isReplaced() {
        String version1 = "c19b472d-7a2a-1d3b-a12b-abcdef123456";

        String generated = CorrelationIds.resolve(version1);

        assertNotEquals(version1, generated);
        assertGeneratedUuidV4(generated);
    }

    /**
     * Un texto que no es un UUID se reemplaza aunque sea corto y legible
     */
    @Test
    void resolve_textThatIsNotAUuid_isReplaced() {
        assertGeneratedUuidV4(CorrelationIds.resolve("c19b-472d-a12b"));
        assertGeneratedUuidV4(CorrelationIds.resolve("a".repeat(100)));
    }

    /**
     * Si la cabecera falta se genera un UUID v4
     */
    @Test
    void resolve_null_generatesAUuidV4() {
        assertGeneratedUuidV4(CorrelationIds.resolve(null));
    }

    /**
     * Un valor en blanco se trata como si faltara
     */
    @Test
    void resolve_blank_generatesAUuidV4() {
        assertGeneratedUuidV4(CorrelationIds.resolve("   "));
    }

    /**
     * Un valor con saltos de línea no es válido y se reemplaza, así no se inyectan líneas en los logs
     */
    @Test
    void resolve_valueWithLineBreak_isReplaced() {
        String generated = CorrelationIds.resolve(VALID + "\r\nFORGED");

        assertGeneratedUuidV4(generated);
        assertTrue(!generated.contains("FORGED"));
    }

    /**
     * Dos peticiones sin cabecera reciben valores distintos
     */
    @Test
    void resolve_twoCallsWithoutValue_generateDifferentValues() {
        assertNotEquals(CorrelationIds.resolve(null), CorrelationIds.resolve(null));
    }

    /**
     * Si un filtro ya resolvió el valor, current devuelve ese mismo valor
     */
    @Test
    void current_withResolvedAttribute_returnsIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CorrelationIds.ATTRIBUTE, VALID);
        request.addHeader(CorrelationIds.HEADER, "1c2f9d34-5b7a-4e0c-9d61-7a4b8e3f2a10");

        assertEquals(VALID, CorrelationIds.current(request));
    }

    /**
     * Si ningún filtro resolvió el valor, current lo resuelve desde la cabecera
     */
    @Test
    void current_withoutAttribute_resolvesTheHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, VALID);

        assertEquals(VALID, CorrelationIds.current(request));
    }

    /**
     * Si no hay atributo ni cabecera, current genera un UUID v4
     */
    @Test
    void current_withoutAttributeNorHeader_generatesAUuidV4() {
        assertGeneratedUuidV4(CorrelationIds.current(new MockHttpServletRequest()));
    }
}
