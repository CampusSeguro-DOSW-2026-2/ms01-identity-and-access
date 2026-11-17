package edu.eci.dosw.campusseguro.identity.identity_service.entity;

/**
 * Permissions of MS01 (RN-MS01-14), the only ones that the code defines and requires in its routes, the complete
 * catalog with the permissions of all the modules lives in the permissions table and MS01 only synchronizes at startup
 * those of this enum
 *
 * Each permission is identified by a code module:resource:action, which roles have it is not defined here: it is stored
 * by the role_permissions table and edited by the administrator (RF-MS01-10)
 */
public enum Permission {
    // MS01-Identity and access
    MS01_PROFILE_READ("ms01:profile:read", "Consultar el propio perfil"),
    MS01_PROFILE_WRITE("ms01:profile:write", "Actualizar el propio perfil (nombres y apellidos)"),
    MS01_PASSWORD_CHANGE("ms01:password:change", "Cambiar la propia contraseña"),
    MS01_SESSION_LOGOUT("ms01:session:logout", "Cerrar sesión"),
    MS01_ROLE_READ_OWN("ms01:role:read_own", "Consultar los propios roles y sus permisos"),
    MS01_ROLE_READ_ALL("ms01:role:read_all",
            "Consultar el catálogo completo de roles, el detalle de cada rol y el catálogo de permisos"),
    MS01_ROLE_MANAGE("ms01:role:manage", "Crear, renombrar, actualizar y borrar roles y asignarles permisos"),
    MS01_USER_READ("ms01:user:read", "Listar, buscar y consultar usuarios"),
    MS01_USER_STATUS("ms01:user:status", "Activar o inactivar usuarios"),
    MS01_USER_ROLES("ms01:user:roles", "Agregar o retirar roles a usuarios");

    public static final String PREFIX = "ms01:";

    private final String code;
    private final String description;

        /**
     * Creates a permission
     *
     * @param code code of the permission
     * @param description description of the permission, it is only used when inserting it into the catalog
     */
    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

        /**
     * Returns the code of the permission
     *
     * @return code of the permission with the format module:resource:action
     */
    public String getCode() {
        return code;
    }

        /**
     * Returns the description of the permission
     *
     * @return description of the permission
     */
    public String getDescription() {
        return description;
    }
}
