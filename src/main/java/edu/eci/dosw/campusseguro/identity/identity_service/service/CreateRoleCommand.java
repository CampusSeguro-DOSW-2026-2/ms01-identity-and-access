package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

/**
 * Role creation request already validated and normalized (form F-10)
 *
 * @param name role name in uppercase
 * @param description role description, trimmed
 * @param permissions permission codes from the catalog, sorted and without repeated ones
 */
public record CreateRoleCommand(String name, String description, List<String> permissions) {
}
