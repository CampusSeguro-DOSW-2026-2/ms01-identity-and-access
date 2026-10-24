package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * Respuesta de POST /api/v1/auth/login
 */
public record LoginResponse(
        String token,
        String tipo,
        @JsonProperty("expira_en") String expiraEn,
        UsuarioResumen usuario) {

    public static final String TOKEN_TYPE = "Bearer";

    public record UsuarioResumen(UUID id, String correo, List<UserRole> roles) {
    }
}
