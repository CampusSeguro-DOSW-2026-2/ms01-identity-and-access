package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of POST /api/v1/users/batch (RF-MS01-09)
 *
 * @param usuarios users found, without repeated ones and in the order of the request, active and inactive
 * @param noEncontrados requested ids that do not match any user, in the order of the request
 */
public record BatchUsersResponse(
        List<UserProfileResponse> usuarios,
        @JsonProperty("no_encontrados") List<UUID> noEncontrados) {
}
