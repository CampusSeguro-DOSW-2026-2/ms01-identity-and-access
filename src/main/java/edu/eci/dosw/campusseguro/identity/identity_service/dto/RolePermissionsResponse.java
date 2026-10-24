package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * Elemento de la respuesta de GET /api/v1/roles (esquema RolePermissions del contrato): un rol con los
 * codigos de los permisos que la matriz Rol-Permiso le concede.
 *
 * @param rol rol del catalogo
 * @param permisos codigos de permiso del rol con el formato ms:recurso:accion, en el orden de la matriz
 */
public record RolePermissionsResponse(UserRole rol, List<String> permisos) {

    /**
     * Construye la respuesta de un rol a partir de la matriz definida en el codigo.
     *
     * @param role rol del catalogo
     * @return el rol junto con los codigos de sus permisos
     */
    public static RolePermissionsResponse from(UserRole role) {
        return new RolePermissionsResponse(role, role.getPermissionCodes());
    }
}
