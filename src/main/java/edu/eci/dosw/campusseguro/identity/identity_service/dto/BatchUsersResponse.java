package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta de POST /api/v1/users/batch (RF-MS01-09)
 *
 * @param usuarios usuarios encontrados, sin repetidos y en el orden de la solicitud, activos e inactivos
 * @param noEncontrados ids solicitados que no corresponden a ningún usuario, en el orden de la solicitud
 */
public record BatchUsersResponse(
        List<UserProfileResponse> usuarios,
        @JsonProperty("no_encontrados") List<UUID> noEncontrados) {
}
