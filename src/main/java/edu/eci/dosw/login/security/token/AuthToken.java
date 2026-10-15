package edu.eci.dosw.login.security.token;

/**
 * Resultado de generar un token de autenticación.
 * FASE 2: implementado por {@link JwtTokenProvider}.
 */
public record AuthToken(
        String token,
        String tokenType,
        String expiresAt,
        String correlationId
) {
}