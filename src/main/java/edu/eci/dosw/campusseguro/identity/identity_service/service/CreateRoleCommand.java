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

    /**
     * Keeps an immutable copy of the permissions of the new role
     *
     * @param name name of the role
     * @param description description of the role
     * @param permissions permission codes of the role
     * @throws NullPointerException if the list or one of its elements is null
     */
    public CreateRoleCommand {
        permissions = List.copyOf(permissions);
    }
}
