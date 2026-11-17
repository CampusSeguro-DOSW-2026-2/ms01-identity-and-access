package edu.eci.dosw.campusseguro.identity.identity_service.service;

/**
 * Role edit request already validated and normalized (form F-11)
 *
 * @param name role name in uppercase, equal to the current one or new
 * @param description role description, trimmed
 */
public record UpdateRoleCommand(String name, String description) {
}
