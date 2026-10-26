package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;

/**
 * Cuerpo estándar de error de los estándares de integración (sección 4)
 */
class ErrorResponseTest {

    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    /**
     * La fábrica crea un error sin detalle por campo
     */
    @Test
    void of_createsAnErrorWithoutDetails() {
        ErrorResponse error = ErrorResponse.of(404, "NO_ENCONTRADO", "Recurso no encontrado.", CORRELATION_ID);

        assertEquals(404, error.status());
        assertEquals("NO_ENCONTRADO", error.codigo());
        assertEquals("Recurso no encontrado.", error.mensaje());
        assertTrue(error.errores().isEmpty());
        assertEquals(CORRELATION_ID, error.correlationId());
    }

    /**
     * Una lista de errores nula se convierte en una lista vacía
     */
    @Test
    void nullDetails_becomeAnEmptyList() {
        ErrorResponse error = new ErrorResponse(400, "VALIDACION", "Datos inválidos.", null, CORRELATION_ID);

        assertTrue(error.errores().isEmpty());
    }

    /**
     * La lista de errores se copia y no se puede modificar
     */
    @Test
    void details_areAnUnmodifiableCopy() {
        List<ErrorResponse.ErrorDetail> source = new ArrayList<>();
        source.add(new ErrorResponse.ErrorDetail("correo", "Correo no válido."));
        ErrorResponse error = new ErrorResponse(400, "VALIDACION", "Datos inválidos.", source, CORRELATION_ID);

        source.clear();

        assertEquals(1, error.errores().size());
        assertThrows(UnsupportedOperationException.class,
                () -> error.errores().add(new ErrorResponse.ErrorDetail("x", "y")));
    }

    /**
     * Sin correlation id se genera uno porque el campo es obligatorio
     */
    @Test
    void missingCorrelationId_isGenerated() {
        ErrorResponse withNull = ErrorResponse.of(400, "VALIDACION", "Datos inválidos.", null);
        ErrorResponse withBlank = ErrorResponse.of(400, "VALIDACION", "Datos inválidos.", "  ");

        assertDoesNotThrow(() -> UUID.fromString(withNull.correlationId()));
        assertDoesNotThrow(() -> UUID.fromString(withBlank.correlationId()));
    }

    /**
     * El JSON usa los nombres del contrato y siempre incluye la lista de errores
     *
     * @throws Exception si falla la serialización
     */
    @Test
    void json_usesTheContractNames() throws Exception {
        ErrorResponse error = new ErrorResponse(400, "VALIDACION", "Datos inválidos.",
                List.of(new ErrorResponse.ErrorDetail("correo", "Correo no válido.")), CORRELATION_ID);

        String json = new ObjectMapper().writeValueAsString(error);

        assertTrue(json.contains("\"status\":400"));
        assertTrue(json.contains("\"codigo\":\"VALIDACION\""));
        assertTrue(json.contains("\"mensaje\":\"Datos inválidos.\""));
        assertTrue(json.contains("\"errores\":[{\"campo\":\"correo\",\"mensaje\":\"Correo no válido.\"}]"));
        assertTrue(json.contains("\"correlation_id\":\"" + CORRELATION_ID + "\""));
        assertFalse(json.contains("correlationId"));
    }
}
