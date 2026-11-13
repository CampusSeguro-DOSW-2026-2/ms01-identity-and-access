package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Detalle de un rol (esquema RoleDetail del contrato), respuesta de las consultas, la creación y la edición
 * de roles
 *
 * @param rol nombre del rol
 * @param descripcion descripción del rol
 * @param permisos codigos de permiso del rol, ordenados y sin repetidos
 * @param system true solo en los roles de sistema, ESTUDIANTE y ADMIN
 */
public record RoleDetailResponse(
        String rol,
        String descripcion,
        List<String> permisos,
        @JsonProperty("es_sistema") boolean system) {
}
