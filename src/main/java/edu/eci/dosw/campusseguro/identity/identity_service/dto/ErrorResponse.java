package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de error normalizado sin exponer trazas internas (RNF-MS01-04), segun el esquema
 * ErrorResponse del contrato OpenAPI y los Estandares de Integracion (seccion 7.1), tiene exactamente
 * cinco campos y ninguno es null
 *
 * @param status mismo codigo HTTP de la respuesta
 * @param codigo codigo del catalogo de estados y codigos
 * @param mensaje texto seguro en español
 * @param errores errores por campo, vacio si no hay errores por campo
 * @param correlationId identico al X-Correlation-ID de la peticion
 */
public record ErrorResponse(
        int status,
        String codigo,
        String mensaje,
        List<ErrorDetail> errores,
        @JsonProperty("correlation_id") String correlationId) {

    /**
     * Garantiza que errores y correlationId nunca sean null y deja los errores como una lista inmutable
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
     * Error de un campo concreto de la peticion
     *
     * @param campo nombre del campo tal como viaja en el contrato
     * @param mensaje regla incumplida por el campo
     */
    public record ErrorDetail(String campo, String mensaje) {
    }

    /**
     * Crea un error sin detalle por campo
     *
     * @param status codigo HTTP de la respuesta
     * @param codigo codigo del catalogo de estados y codigos
     * @param mensaje texto seguro en español
     * @param correlationId correlation id de la peticion
     * @return el error sin errores por campo
     */
    public static ErrorResponse of(int status, String codigo, String mensaje, String correlationId) {
        return new ErrorResponse(status, codigo, mensaje, List.of(), correlationId);
    }
}
