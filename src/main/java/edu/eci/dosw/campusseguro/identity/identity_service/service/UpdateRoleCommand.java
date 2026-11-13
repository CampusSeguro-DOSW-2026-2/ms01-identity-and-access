package edu.eci.dosw.campusseguro.identity.identity_service.service;

/**
 * Solicitud de edición de un rol ya validada y normalizada (formulario F-11)
 *
 * @param name nombre del rol en mayúsculas, igual al actual o nuevo
 * @param description descripción del rol, recortada
 */
public record UpdateRoleCommand(String name, String description) {
}
