package edu.eci.dosw.campusseguro.identity.identity_service.dto;

/**
 * Elemento de la respuesta de GET /api/v1/permissions (esquema PermissionItem del contrato)
 *
 * @param permiso codigo del permiso con el formato ms:recurso:accion
 * @param descripcion descripcion del permiso
 */
public record PermissionItemResponse(String permiso, String descripcion) {
}
