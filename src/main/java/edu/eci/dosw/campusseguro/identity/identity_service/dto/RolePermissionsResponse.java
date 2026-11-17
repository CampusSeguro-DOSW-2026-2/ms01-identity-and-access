package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

/**
 * Element of the response of GET /api/v1/roles (RolePermissions schema of the contract): a role with the codes of its
 * permissions
 *
 * @param rol name of the role
 * @param permisos permission codes of the role with the format ms:resource:action, sorted and without repeated ones
 */
public record RolePermissionsResponse(String rol, List<String> permisos) {
}
