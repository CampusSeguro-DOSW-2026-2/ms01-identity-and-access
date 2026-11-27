package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;

/**
 * Permission codes: their format and the calculation of the effective permissions of a user from the stored roles
 */
public final class PermissionCodes {

    private static final Pattern FORMAT = Pattern.compile("^ms[0-9]{2}:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$");

        /**
     * Utility class, it is not instantiated
     */
    private PermissionCodes() {
    }

        /**
     * Tells whether a code has the format module:resource:action, without checking that it is in the catalog
     *
     * @param code code to check, may be null
     * @return true if the code has the format of a permission
     */
    public static boolean hasFormat(String code) {
        return code != null && FORMAT.matcher(code).matches();
    }

        /**
     * Returns the permissions of a role as they are shown and used: the codes of its role_permissions rows, without
     * repeated ones and in alphabetical order
     *
     * @param role role, as it is stored
     * @return the permission codes of the role
     */
    public static List<String> ofRole(RoleEntity role) {
        TreeSet<String> codes = new TreeSet<>();
        for (PermissionEntity permission : role.getPermissions()) {
            codes.add(permission.getName());
        }
        return List.copyOf(codes);
    }

        /**
     * Calculates the effective permissions of a user: the union of the permissions of its roles
     *
     * @param roles roles of the user, as they are stored
     * @return the permission codes in alphabetical order and without repeated ones
     */
    public static List<String> effective(Collection<RoleEntity> roles) {
        TreeSet<String> codes = new TreeSet<>();
        for (RoleEntity role : roles) {
            codes.addAll(ofRole(role));
        }
        return List.copyOf(codes);
    }
}
