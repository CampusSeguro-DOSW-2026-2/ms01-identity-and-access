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

    /**
     * Datos mínimos del usuario autenticado que acompañan al token
     *
     * @param id identificador del usuario
     * @param correo correo institucional del usuario
     * @param roles roles del usuario en el momento de emitir el token
     */
    public record UsuarioResumen(UUID id, String correo, List<UserRole> roles) {
    }
}
