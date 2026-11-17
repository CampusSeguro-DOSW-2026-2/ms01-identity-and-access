package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Normalized error body that does not expose internal traces (RNF-MS01-04), following the ErrorResponse schema of the
 * OpenAPI contract and the Integration Standards (section 7.1), it has exactly five fields and none of them is null
 *
 * @param status same HTTP code of the response
 * @param codigo code of the catalog of statuses and codes
 * @param mensaje safe text in Spanish
 * @param errores errors by field, empty if there are no errors by field
 * @param correlationId identical to the X-Correlation-ID of the request
 */
public record ErrorResponse(
        int status,
        String codigo,
        String mensaje,
        List<ErrorDetail> errores,
        @JsonProperty("correlation_id") String correlationId) {

        /**
     * Guarantees that errors and correlationId are never null and leaves the errors as an immutable list
     */
    public ErrorResponse {
        if (errores == null) {
            errores = List.of();
        } else {
            errores = List.copyOf(errores);
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
    }

        /**
     * Error of a specific field of the request
     *
     * @param campo name of the field as it travels in the contract
     * @param mensaje rule broken by the field
     */
    public record ErrorDetail(String campo, String mensaje) {
    }

        /**
     * Creates an error without detail by field
     *
     * @param status HTTP code of the response
     * @param codigo code of the catalog of statuses and codes
     * @param mensaje safe text in Spanish
     * @param correlationId correlation id of the request
     * @return the error without errors by field
     */
    public static ErrorResponse of(int status, String codigo, String mensaje, String correlationId) {
        return new ErrorResponse(status, codigo, mensaje, List.of(), correlationId);
    }
}
