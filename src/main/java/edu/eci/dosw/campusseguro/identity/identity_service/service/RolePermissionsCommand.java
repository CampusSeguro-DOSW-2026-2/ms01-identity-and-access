package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

/**
 * Change request of the permissions of a role already validated (form F-12)
 *
 * @param permissions permission codes from the catalog, sorted and without repeated ones
 */
public record RolePermissionsCommand(List<String> permissions) {
}
