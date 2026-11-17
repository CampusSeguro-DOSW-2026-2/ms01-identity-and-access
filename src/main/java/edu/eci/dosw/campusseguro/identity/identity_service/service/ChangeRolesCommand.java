package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.Set;
import java.util.UUID;

/**
 * Already validated role change request: without repeated ids, with role names of valid format, without repeated roles
 * and with at least one of the two lists with content
 *
 * @param userIds users
 * @param rolesToAdd roles added to each user
 * @param rolesToRemove roles removed from each user
 */
public record ChangeRolesCommand(Set<UUID> userIds, Set<String> rolesToAdd, Set<String> rolesToRemove) {
}
