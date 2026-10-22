package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.util.MiniJson;

/**
 * Serializador usado para el JWT y para los eventos de auditoria.
 */
class MiniJsonTest {

    @Test
    void write_objectKeepsInsertionOrder() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("z", "1");
        map.put("a", "2");

        assertEquals("{\"z\":\"1\",\"a\":\"2\"}", MiniJson.write(map));
    }

    @Test
    void write_supportsNestedValues() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("id", 7L);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("roles", List.of("A", "B"));
        map.put("usuario", inner);
        map.put("activo", true);
        map.put("nada", null);

        assertEquals("{\"roles\":[\"A\",\"B\"],\"usuario\":{\"id\":7},\"activo\":true,\"nada\":null}",
                MiniJson.write(map));
    }

    @Test
    void write_emptyContainers() {
        assertEquals("{}", MiniJson.write(Map.of()));
        assertEquals("[]", MiniJson.write(List.of()));
    }

    @Test
    void write_enumsUseTheirName() {
        assertEquals("[\"ADMIN\",\"AUDITOR\"]", MiniJson.write(List.of(UserRole.ADMIN, UserRole.AUDITOR)));
    }

    @Test
    void write_numbers() {
        assertEquals("[1,2,3,1.5,10]", MiniJson.write(List.of((byte) 1, (short) 2, 3L, 1.5d, new BigDecimal("10"))));
    }

    @Test
    void write_escapesQuotesBackslashesAndControlCharacters() {
        assertEquals("\"a\\\"b\\\\c\\n\\r\\t\\b\\f\"", MiniJson.write("a\"b\\c\n\r\t\b\f"));
        assertEquals("\"\\u0001\\u001f\"", MiniJson.write("\u0001\u001f"));
    }

    @Test
    void write_escapesUnicodeLineSeparators() {
        assertEquals("\"\\u2028\\u2029\"", MiniJson.write("  "));
    }

    @Test
    void write_keepsNonAsciiTextAsIs() {
        assertEquals("\"María Pérez ñ\"", MiniJson.write("María Pérez ñ"));
    }

    @Test
    void write_nullIsJsonNull() {
        assertEquals("null", MiniJson.write(null));
    }

    @Test
    void write_rejectsUnsupportedTypesAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.write(Double.POSITIVE_INFINITY));
    }
}
