package edu.eci.dosw.login.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta del inicio de sesion.
 *
 * FASE 1 (MVP): no se emite JWT; {@code tokenType}, {@code accessToken} y
 * {@code expiresAt} son reservados y quedan en {@code null}. El contrato JSON
 * se mantiene estable para que la FASE 2 solo tenga que rellenarlos.
 */
@Schema(description = "Resultado del inicio de sesion (extensible a JWT en Fase 2)")
public record LoginResponse(

        @Schema(description = "Indica si las credenciales fueron validas", example = "true")
        boolean authenticated,

        @Schema(description = "Informacion publica del usuario autenticado")
        RegisterResponse user,

        @Schema(description = "Tipo de token (reservado para Fase 2)", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token JWT; null en el MVP", nullable = true)
        String accessToken,

        @Schema(description = "Fecha de expiracion del token; null en el MVP", nullable = true)
        String expiresAt
) {

    /** Respuesta de login del MVP (aun sin token). */
    public static LoginResponse authenticated(RegisterResponse user) {
        return new LoginResponse(true, user, null, null, null);
    }
}