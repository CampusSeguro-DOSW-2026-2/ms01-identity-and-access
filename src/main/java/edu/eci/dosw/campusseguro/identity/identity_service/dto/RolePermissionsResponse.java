package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

/**
 * Elemento de la respuesta de GET /api/v1/roles (esquema RolePermissions del contrato): un rol con los
 * codigos de sus permisos
 *
 * @param rol nombre del rol
 * @param permisos codigos de permiso del rol con el formato ms:recurso:accion, ordenados y sin repetidos
 */
public record RolePermissionsResponse(String rol, List<String> permisos) {
}
