package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * Identidad de quien hace la peticion
 *
 * @param userId id del usuario autenticado
 * @param roles roles del usuario
 */
public record RequesterIdentity(UUID userId, Set<UserRole> roles) {

    /**
     * Valida los componentes y deja los roles como un conjunto inmutable
     */
    public RequesterIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roles, "roles");
        EnumSet<UserRole> copy = EnumSet.noneOf(UserRole.class);
        copy.addAll(roles);
        roles = Collections.unmodifiableSet(copy);
    }

    /**
     * Calcula los permisos del solicitante
     *
     * @return conjunto con los permisos de todos los roles del solicitante
     */
    public Set<Permission> permissions() {
        return UserRole.permissionsOf(roles);
    }

    /**
     * Indica si alguno de los roles del solicitante concede el permiso
     *
     * @param permission permiso que se desea comprobar
     * @return true si sus permisos contienen el permiso a comprobar, false en caso contrario
     */
    public boolean hasPermission(Permission permission) {
        return permissions().contains(permission);
    }

    /**
     * Devuelve los roles del solicitante con el mismo formato de la cabecera X-User-Role, separados por
     * coma, sin espacios y en orden alfabetico (Estandares de Integracion, seccion 4)
     *
     * @return los nombres de los roles separados por coma
     */
    public String rolesHeader() {
        List<String> names = new ArrayList<>();
        for (UserRole role : UserRole.sortedByName(roles)) {
            names.add(role.name());
        }
        return String.join(",", names);
    }
}
