package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Detail of a role (RoleDetail schema of the contract), response of the queries, the creation and the edition of roles
 *
 * @param rol name of the role
 * @param descripcion description of the role
 * @param permisos permission codes of the role, sorted and without repeated ones
 * @param system true only for the system roles, ESTUDIANTE and ADMIN
 */
public record RoleDetailResponse(
        String rol,
        String descripcion,
        List<String> permisos,
        @JsonProperty("es_sistema") boolean system) {
}
