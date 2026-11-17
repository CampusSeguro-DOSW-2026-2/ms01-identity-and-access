package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of GET /api/v1/internal/session-cuts (SessionCuts schema of the contract): the snapshot of the session cuts
 * in force (RF-MS01-11)
 *
 * @param generatedAt instant at which the response was built, in ISO 8601 UTC with seconds
 * @param windowSeconds window in seconds within which a cut remains in force
 * @param usuarios users with a cut in force
 * @param roles roles with a cut in force
 */
public record SessionCutsResponse(
        @JsonProperty("generado_en") String generatedAt,
        @JsonProperty("ventana_segundos") long windowSeconds,
        List<UserCut> usuarios,
        List<RoleCut> roles) {

        /**
     * Session cut of a user
     *
     * @param id id of the user
     * @param validFrom instant from which its credentials are valid, in ISO 8601 UTC with seconds
     */
    public record UserCut(String id, @JsonProperty("valido_desde") String validFrom) {
    }

        /**
     * Session cut of a role
     *
     * @param rol name of the role
     * @param validFrom instant from which the credentials of its users are valid, in ISO 8601 UTC with seconds
     */
    public record RoleCut(String rol, @JsonProperty("valido_desde") String validFrom) {
    }
}
