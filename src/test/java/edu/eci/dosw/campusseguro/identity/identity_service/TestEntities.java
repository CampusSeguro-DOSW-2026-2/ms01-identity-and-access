package edu.eci.dosw.campusseguro.identity.identity_service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;

/**
 * Factory of test entities: catalog permissions and roles with their permissions, as the database would return them
 */
public final class TestEntities {

        /**
     * Utility class, it is not instantiated
     */
    private TestEntities() {
    }

        /**
     * Creates a catalog permission with a fixed description
     *
     * @param code code of the permission
     * @return the entity
     */
    public static PermissionEntity permission(String code) {
        PermissionEntity permission = new PermissionEntity();
        permission.setName(code);
        permission.setDescription("Descripción de " + code);
        return permission;
    }

        /**
     * Creates the set of permissions of a role from their codes, the repeated codes give a single permission
     *
     * @param codes permission codes
     * @return the permissions, without repeated ones
     */
    public static Set<PermissionEntity> permissions(String... codes) {
        Set<String> distinct = new HashSet<>(List.of(codes));
        Set<PermissionEntity> permissions = new HashSet<>();
        for (String code : distinct) {
            permissions.add(permission(code));
        }
        return permissions;
    }

        /**
     * Creates a non-system role with the indicated permissions
     *
     * @param name name of the role
     * @param codes codes of the permissions of the role
     * @return the entity
     */
    public static RoleEntity role(String name, String... codes) {
        RoleEntity role = new RoleEntity();
        role.setName(name);
        role.setDescription("Descripción de " + name);
        role.setPermissions(permissions(codes));
        return role;
    }

        /**
     * Creates a set of test roles from their names, each one with the ms01:profile:read permission
     *
     * @param names names of the roles
     * @return the roles, without repeated ones per instance
     */
    public static Set<RoleEntity> roles(String... names) {
        Set<RoleEntity> roles = new HashSet<>();
        for (String name : names) {
            roles.add(role(name, "ms01:profile:read"));
        }
        return roles;
    }
}
