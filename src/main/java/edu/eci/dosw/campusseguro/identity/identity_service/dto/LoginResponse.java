package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of POST /api/v1/auth/login
 */
public record LoginResponse(
        String token,
        String tipo,
        @JsonProperty("expira_en") String expiraEn,
        UserSummary usuario) {

    public static final String TOKEN_TYPE = "Bearer";

        /**
     * Minimum data of the authenticated user that goes with the token
     *
     * @param id identifier of the user
     * @param correo institutional email of the user
     * @param roles roles of the user at the moment the token was issued
     */
    public record UserSummary(UUID id, String correo, List<String> roles) {
    }
}
