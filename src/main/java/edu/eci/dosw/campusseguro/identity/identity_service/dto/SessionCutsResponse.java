package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta de GET /api/v1/internal/session-cuts (esquema SessionCuts del contrato): la instantánea de los
 * cortes de sesión vigentes (RF-MS01-11)
 *
 * @param generatedAt instante en que se armó la respuesta, en ISO 8601 UTC con segundos
 * @param windowSeconds ventana en segundos dentro de la cual un corte sigue vigente
 * @param usuarios usuarios con corte vigente
 * @param roles roles con corte vigente
 */
public record SessionCutsResponse(
        @JsonProperty("generado_en") String generatedAt,
        @JsonProperty("ventana_segundos") long windowSeconds,
        List<UserCut> usuarios,
        List<RoleCut> roles) {

    /**
     * Corte de sesión de un usuario
     *
     * @param id id del usuario
     * @param validFrom instante desde el cual son válidas sus credenciales, en ISO 8601 UTC con segundos
     */
    public record UserCut(String id, @JsonProperty("valido_desde") String validFrom) {
    }

    /**
     * Corte de sesión de un rol
     *
     * @param rol nombre del rol
     * @param validFrom instante desde el cual son válidas las credenciales de sus usuarios, en ISO 8601 UTC con
     *        segundos
     */
    public record RoleCut(String rol, @JsonProperty("valido_desde") String validFrom) {
    }
}
