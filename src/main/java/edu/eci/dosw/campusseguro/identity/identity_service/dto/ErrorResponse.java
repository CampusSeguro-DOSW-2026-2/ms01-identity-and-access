package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de error normalizado (RNF-MS01-04), segun el esquema ErrorResponse del contrato OpenAPI.
 * Nunca incluye trazas internas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String codigo,
        String mensaje,
        List<ErrorDetail> errores,
        @JsonProperty("correlation_id") String correlationId) {

    /** Detalle por campo (RG-05). */
    public record ErrorDetail(String campo, String mensaje) {
    }

    public static ErrorResponse of(int status, String codigo, String mensaje, String correlationId) {
        return new ErrorResponse(status, codigo, mensaje, List.of(), correlationId);
    }
}
