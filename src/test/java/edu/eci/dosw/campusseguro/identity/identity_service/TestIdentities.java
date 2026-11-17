package edu.eci.dosw.campusseguro.identity.identity_service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Builds test identities with the permissions that the roles of a freshly created database would have
 */
public final class TestIdentities {

        /**
     * Utility class, it is not instantiated
     */
    private TestIdentities() {
    }

        /**
     * Computes the union of the permissions of the indicated roles
     *
     * @param roles names of seeded roles
     * @return permission codes of the union, empty if no role is seeded
     */
    public static Set<String> permissionsOf(String... roles) {
        Set<String> permissions = new TreeSet<>();
        for (String role : roles) {
            permissions.addAll(SeedRoles.PERMISSIONS.getOrDefault(role, List.of()));
        }
        return permissions;
    }

        /**
     * Builds the value of the X-User-Permission header with the union of the permissions of the seeded roles
     *
     * @param rolesCsv names of seeded roles separated by comma, spaces are ignored
     * @return permission codes separated by comma, empty if no role is seeded
     */
    public static String permissionHeader(String rolesCsv) {
        String[] roles = rolesCsv.split(",");
        for (int i = 0; i < roles.length; i++) {
            roles[i] = roles[i].trim();
        }
        return String.join(",", permissionsOf(roles));
    }

        /**
     * Creates an identity whose permissions are the union of the permissions of the indicated roles
     *
     * @param userId identifier of the user
     * @param roles names of seeded roles
     * @return identity with those roles and the union of their permissions
     */
    public static RequesterIdentity of(UUID userId, String... roles) {
        return new RequesterIdentity(userId, new TreeSet<>(Arrays.asList(roles)), permissionsOf(roles));
    }

        /**
     * Creates an identity with the indicated roles and exactly the indicated permissions
     *
     * @param userId identifier of the user
     * @param roles roles of the identity
     * @param permissions permission codes of the identity
     * @return identity with those roles and those permissions
     */
    public static RequesterIdentity withPermissions(UUID userId, Set<String> roles, String... permissions) {
        return new RequesterIdentity(userId, roles, new TreeSet<>(Arrays.asList(permissions)));
    }
}
