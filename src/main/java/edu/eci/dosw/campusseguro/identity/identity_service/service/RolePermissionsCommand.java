package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

/**
 * Change request of the permissions of a role already validated (form F-12)
 *
 * @param permissions permission codes from the catalog, sorted and without repeated ones
 */
public record RolePermissionsCommand(List<String> permissions) {

    /**
     * Keeps an immutable copy of the new permissions of the role
     *
     * @param permissions permission codes of the role
     * @throws NullPointerException if the list or one of its elements is null
     */
    public RolePermissionsCommand {
        permissions = List.copyOf(permissions);
    }
}
