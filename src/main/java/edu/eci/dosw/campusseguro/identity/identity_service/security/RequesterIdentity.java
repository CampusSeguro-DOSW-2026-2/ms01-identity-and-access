package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * Identidad de quien hace la peticion, tal como la entrega el API Gateway en las cabeceras
 *
 * @param userId id del usuario autenticado
 * @param roles nombres de los roles del usuario (X-User-Role)
 * @param permissions códigos de los permisos efectivos del usuario (X-User-Permission)
 */
public record RequesterIdentity(UUID userId, Set<String> roles, Set<String> permissions) {

    /**
     * Valida los componentes y deja los roles y los permisos como conjuntos inmutables
     */
    public RequesterIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(permissions, "permissions");
        roles = Collections.unmodifiableSet(new TreeSet<>(roles));
        permissions = Collections.unmodifiableSet(new TreeSet<>(permissions));
    }

    /**
     * Indica si el solicitante tiene el permiso, la autorización se decide solo con los permisos de la cabecera
     * X-User-Permission y nunca con el nombre de un rol
     *
     * @param permission permiso que se desea comprobar
     * @return true si sus permisos contienen el permiso a comprobar, false en caso contrario
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission.getCode());
    }

    /**
     * Devuelve los roles del solicitante con el mismo formato de la cabecera X-User-Role, separados por
     * coma, sin espacios y en orden alfabetico (Estandares de Integracion, seccion 4)
     *
     * @return los nombres de los roles separados por coma
     */
    public String rolesHeader() {
        return String.join(",", RoleNames.sorted(roles));
    }
}
