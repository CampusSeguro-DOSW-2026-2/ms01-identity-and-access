package edu.eci.dosw.campusseguro.identity.identity_service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;

/**
 * Fábrica de entidades de prueba: permisos del catálogo y roles con sus permisos, tal como los devolvería la base
 * de datos
 */
public final class TestEntities {

    /**
     * Clase de utilidades, no se instancia
     */
    private TestEntities() {
    }

    /**
     * Crea un permiso del catálogo con una descripción fija
     *
     * @param code código del permiso
     * @return la entidad
     */
    public static PermissionEntity permission(String code) {
        PermissionEntity permission = new PermissionEntity();
        permission.setName(code);
        permission.setDescription("Descripción de " + code);
        return permission;
    }

    /**
     * Crea el conjunto de permisos de un rol a partir de sus códigos, los códigos repetidos dan un solo permiso
     *
     * @param codes códigos de permiso
     * @return los permisos, sin repetidos
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
     * Crea un rol que no es de sistema con los permisos indicados
     *
     * @param name nombre del rol
     * @param codes códigos de los permisos del rol
     * @return la entidad
     */
    public static RoleEntity role(String name, String... codes) {
        RoleEntity role = new RoleEntity();
        role.setName(name);
        role.setDescription("Descripción de " + name);
        role.setPermissions(permissions(codes));
        return role;
    }

    /**
     * Crea un conjunto de roles de prueba a partir de sus nombres, cada uno con el permiso ms01:profile:read
     *
     * @param names nombres de los roles
     * @return los roles, sin repetidos por instancia
     */
    public static Set<RoleEntity> roles(String... names) {
        Set<RoleEntity> roles = new HashSet<>();
        for (String name : names) {
            roles.add(role(name, "ms01:profile:read"));
        }
        return roles;
    }
}
