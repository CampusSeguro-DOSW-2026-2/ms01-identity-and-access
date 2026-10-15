package edu.eci.dosw.login.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Formato estandarizado de error para consumo del frontend.
 * El correlationId es opcional en la Fase 1 (se omite si es null).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta de error estandarizada")
public record ApiError(

        @Schema(example = "2026-10-14T12:34:56.789Z")
        Instant timestamp,

        @Schema(example = "400")
        int status,

        @Schema(example = "Bad Request")
        String error,

        @Schema(example = "El email ya está registrado")
        String message,

        @Schema(example = "/api/v1/auth/register")
        String path,

        @Schema(nullable = true, description = "Solo presente si la Fase 2 (Gateway) lo propaga")
        String correlationId,

        @Schema(nullable = true, description = "Detalle de errores de validación por campo")
        Map<String, String> details
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null, null);
    }

    public static ApiError of(int status, String error, String message, String path, Map<String, String> details) {
        return new ApiError(Instant.now(), status, error, message, path, null, details);
    }
}