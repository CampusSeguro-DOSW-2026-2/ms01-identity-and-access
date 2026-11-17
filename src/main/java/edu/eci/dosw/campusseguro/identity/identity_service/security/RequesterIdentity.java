package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * Identity of whoever makes the request, as delivered by the API Gateway in the headers
 *
 * @param userId id of the authenticated user
 * @param roles names of the roles of the user (X-User-Role)
 * @param permissions codes of the effective permissions of the user (X-User-Permission)
 */
public record RequesterIdentity(UUID userId, Set<String> roles, Set<String> permissions) {

        /**
     * Validates the components and leaves the roles and the permissions as immutable sets
     */
    public RequesterIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(permissions, "permissions");
        roles = Collections.unmodifiableSet(new TreeSet<>(roles));
        permissions = Collections.unmodifiableSet(new TreeSet<>(permissions));
    }

        /**
     * Tells whether the requester has the permission, the authorization is decided only with the permissions of the
     * X-User-Permission header and never with the name of a role
     *
     * @param permission permission to be checked
     * @return true if its permissions contain the permission to be checked, false otherwise
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission.getCode());
    }

        /**
     * Returns the roles of the requester with the same format as the X-User-Role header, separated by comma, without
     * spaces and in alphabetical order (Integration Standards, section 4)
     *
     * @return the names of the roles separated by comma
     */
    public String rolesHeader() {
        return String.join(",", RoleNames.sorted(roles));
    }
}
