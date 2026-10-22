package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * Respuesta de POST /api/v1/auth/login (esquema LoginResponse del contrato).
 *
 * Nunca contiene la contrasena ni su hash. {@code expira_en} es el claim {@code exp} del JWT
 * expresado en ISO 8601 (UTC).
 */
public record LoginResponse(
        String token,
        String tipo,
        @JsonProperty("expira_en") String expiraEn,
        UsuarioResumen usuario) {

    /** Valor de {@code tipo}: el token se envia en {@code Authorization: Bearer <JWT>}. */
    public static final String TOKEN_TYPE = "Bearer";

    /** Datos basicos del usuario autenticado (id, correo, roles). */
    public record UsuarioResumen(UUID id, String correo, List<UserRole> roles) {
    }
}
