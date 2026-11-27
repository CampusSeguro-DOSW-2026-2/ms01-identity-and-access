package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.Collections;
import java.util.LinkedHashSet;
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

    /**
     * Keeps read-only copies of the users and roles of the change, in the order in which they were received
     *
     * @param userIds users whose roles change
     * @param rolesToAdd roles to add
     * @param rolesToRemove roles to remove
     * @throws NullPointerException if a set is null
     */
    public ChangeRolesCommand {
        userIds = Collections.unmodifiableSet(new LinkedHashSet<>(userIds));
        rolesToAdd = Collections.unmodifiableSet(new LinkedHashSet<>(rolesToAdd));
        rolesToRemove = Collections.unmodifiableSet(new LinkedHashSet<>(rolesToRemove));
    }
}
