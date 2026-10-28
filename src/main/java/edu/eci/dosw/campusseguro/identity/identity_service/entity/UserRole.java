package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Roles de CampusSeguro y sus permisos (RN-MS01-14)
 */
public enum UserRole {

    ESTUDIANTE(Permission.grantedToCommunity()),

    PROFESOR(
            Permission.grantedToCommunity(),
            Permission.MS05_INCIDENT_REPORT),

    ADMINISTRATIVO(Permission.grantedToCommunity()),

    BRIGADISTA(
            Permission.grantedToCommunity(),
            Permission.MS04_DASHBOARD_READ,
            Permission.MS05_PANEL_READ_OWN,
            Permission.MS05_ASSISTANCE_READ,
            Permission.MS05_ASSISTANCE_REGISTER,
            Permission.MS05_ASSISTANCE_UPDATE_STATUS,
            Permission.MS05_INCIDENT_REPORT,
            Permission.MS05_INCIDENT_READ,
            Permission.MS05_INCIDENT_UPDATE),

    COORDINADOR(
            Permission.grantedToCommunity(),
            Permission.MS02_ROUTE_STATUS_WRITE,
            Permission.MS03_ALERT_MANAGE,
            Permission.MS03_ALERT_READ_HISTORY,
            Permission.MS04_DASHBOARD_READ,
            Permission.MS04_HISTORY_READ,
            Permission.MS05_BRIGADIER_ASSIGN,
            Permission.MS05_ASSISTANCE_READ,
            Permission.MS05_ASSISTANCE_REGISTER,
            Permission.MS05_ASSISTANCE_ASSIGN,
            Permission.MS05_ASSISTANCE_UPDATE_STATUS,
            Permission.MS05_INCIDENT_REPORT,
            Permission.MS05_INCIDENT_READ,
            Permission.MS05_INCIDENT_UPDATE,
            Permission.MS06_DRILL_READ,
            Permission.MS06_DRILL_COMPARE_REPORT,
            Permission.MS08_INDICATORS_READ),

    ORGANIZADOR(
            Permission.grantedToCommunity(),
            Permission.MS03_ALERT_MANAGE_DRILL,
            Permission.MS03_ALERT_READ_HISTORY,
            Permission.MS04_DASHBOARD_READ,
            Permission.MS04_HISTORY_READ,
            Permission.MS06_DRILL_MANAGE,
            Permission.MS06_DRILL_EXECUTE,
            Permission.MS06_DRILL_EVALUATE,
            Permission.MS06_DRILL_READ,
            Permission.MS06_DRILL_COMPARE_REPORT,
            Permission.MS08_INDICATORS_READ),

    ADMIN(
            Permission.grantedToEveryRole(),
            Permission.MS01_ROLE_READ_ALL,
            Permission.MS01_USER_READ,
            Permission.MS01_USER_STATUS,
            Permission.MS01_USER_ROLES,
            Permission.MS02_BUILDING_WRITE,
            Permission.MS02_SPACE_WRITE,
            Permission.MS02_SAFE_ZONE_WRITE,
            Permission.MS02_ROUTE_WRITE,
            Permission.MS02_ROUTE_STATUS_WRITE,
            Permission.MS02_QR_MANAGE,
            Permission.MS07_CONTENT_MANAGE,
            Permission.MS08_AUDIT_READ),

    AUDITOR(
            Permission.grantedToEveryRole(),
            Permission.MS01_USER_READ,
            Permission.MS03_ALERT_READ_HISTORY,
            Permission.MS04_DASHBOARD_READ,
            Permission.MS04_HISTORY_READ,
            Permission.MS05_INCIDENT_READ,
            Permission.MS06_DRILL_READ,
            Permission.MS06_DRILL_COMPARE_REPORT,
            Permission.MS08_INDICATORS_READ,
            Permission.MS08_AUDIT_READ);

    private final Set<Permission> permissions;

    /**
     * Crea un rol con sus permisos
     *
     * @param basePermissions permisos comunes
     * @param extraPermissions permisos adicionales propios del rol (puede no haber ninguno)
     */
    UserRole(Set<Permission> basePermissions, Permission... extraPermissions) {
        EnumSet<Permission> granted = EnumSet.noneOf(Permission.class);
        granted.addAll(basePermissions);
        granted.addAll(Arrays.asList(extraPermissions));
        this.permissions = Collections.unmodifiableSet(granted);
    }

    /**
     * Devuelve los permisos que la matriz concede al rol
     *
     * @return conjunto de permisos del rol
     */
    public Set<Permission> getPermissions() {
        return permissions;
    }

    /**
     * Indica si el rol tiene un permiso
     *
     * @param permission permiso que se desea comprobar
     * @return true si la matriz concede el permiso al rol; false en caso contrario
     */
    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * Devuelve los codigos de los permisos del rol
     *
     * @return lista de codigos modulo:recurso:accion
     */
    public List<String> getPermissionCodes() {
        return permissions.stream()
                .map(permission -> permission.getCode())
                .toList();
    }

    /**
     * Calcula los permisos de un usuario con varios roles, la union de los permisos de cada uno (MR-01,
     * RN-MS01-13)
     *
     * @param roles roles del usuario
     * @return conjunto con la union de los permisos de todos los roles recibidos
     */
    public static Set<Permission> permissionsOf(Collection<UserRole> roles) {
        EnumSet<Permission> union = EnumSet.noneOf(Permission.class);
        for (UserRole role : roles) {
            union.addAll(role.getPermissions());
        }
        return union;
    }

    /**
     * Ordena alfabeticamente por nombre un grupo de roles, los roles de un usuario se guardan en un
     * conjunto sin orden y las respuestas los entregan ordenados (5_modelo_datos)
     *
     * @param roles roles a ordenar, null equivale a ninguno
     * @return lista de solo lectura con los roles ordenados por nombre
     */
    public static List<UserRole> sortedByName(Collection<UserRole> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .sorted(Comparator.comparing(UserRole::name))
                .toList();
    }
}
