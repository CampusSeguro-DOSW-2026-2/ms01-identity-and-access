package edu.eci.dosw.campusseguro.identity.identity_service.dto;

/**
 * Element of the response of GET /api/v1/roles/alert-recipients (AlertRecipientRole schema of the contract): a role
 * that can receive alerts, without its permissions
 *
 * @param rol name of the role
 * @param descripcion description of the role
 */
public record AlertRecipientRoleResponse(String rol, String descripcion) {
}
