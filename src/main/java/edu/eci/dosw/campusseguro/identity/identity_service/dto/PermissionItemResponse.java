package edu.eci.dosw.campusseguro.identity.identity_service.dto;

/**
 * Element of the response of GET /api/v1/permissions (PermissionItem schema of the contract)
 *
 * @param permiso code of the permission with the format ms:resource:action
 * @param descripcion description of the permission
 */
public record PermissionItemResponse(String permiso, String descripcion) {
}
