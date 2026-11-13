package edu.eci.dosw.campusseguro.identity.identity_service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Arma identidades de prueba con los permisos que tendrían los roles de una base recién creada
 */
public final class TestIdentities {

    /**
     * Clase de utilidades, no se instancia
     */
    private TestIdentities() {
    }

    /**
     * Calcula la unión de los permisos de los roles indicados
     *
     * @param roles nombres de roles sembrados
     * @return códigos de permiso de la unión, vacío si ningún rol está sembrado
     */
    public static Set<String> permissionsOf(String... roles) {
        Set<String> permissions = new TreeSet<>();
        for (String role : roles) {
            permissions.addAll(SeedRoles.PERMISSIONS.getOrDefault(role, List.of()));
        }
        return permissions;
    }

    /**
     * Arma el valor de la cabecera X-User-Permission con la unión de los permisos de los roles sembrados
     *
     * @param rolesCsv nombres de roles sembrados separados por coma, se ignoran los espacios
     * @return códigos de permiso separados por coma, vacío si ningún rol está sembrado
     */
    public static String permissionHeader(String rolesCsv) {
        String[] roles = rolesCsv.split(",");
        for (int i = 0; i < roles.length; i++) {
            roles[i] = roles[i].trim();
        }
        return String.join(",", permissionsOf(roles));
    }

    /**
     * Crea una identidad cuyos permisos son la unión de los permisos de los roles indicados
     *
     * @param userId identificador del usuario
     * @param roles nombres de roles sembrados
     * @return identidad con esos roles y la unión de sus permisos
     */
    public static RequesterIdentity of(UUID userId, String... roles) {
        return new RequesterIdentity(userId, new TreeSet<>(Arrays.asList(roles)), permissionsOf(roles));
    }

    /**
     * Crea una identidad con los roles indicados y exactamente los permisos indicados
     *
     * @param userId identificador del usuario
     * @param roles roles de la identidad
     * @param permissions códigos de permiso de la identidad
     * @return identidad con esos roles y esos permisos
     */
    public static RequesterIdentity withPermissions(UUID userId, Set<String> roles, String... permissions) {
        return new RequesterIdentity(userId, roles, new TreeSet<>(Arrays.asList(permissions)));
    }
}
