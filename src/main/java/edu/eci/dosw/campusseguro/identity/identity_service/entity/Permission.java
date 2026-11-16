package edu.eci.dosw.campusseguro.identity.identity_service.entity;

/**
 * Permisos de MS01 (RN-MS01-14), los únicos que el código define y exige en sus rutas, el catálogo completo con
 * los permisos de todos los módulos vive en la tabla permisos y MS01 solo sincroniza al arrancar los de este enum
 *
 * Cada permiso se identifica con un código modulo:recurso:accion, qué roles lo tienen no se define aquí: lo
 * guarda la tabla rol_permiso y lo edita el administrador (RF-MS01-10)
 */
public enum Permission {
    // MS01-Identidad y acceso
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
     * Crea un permiso
     *
     * @param code código del permiso
     * @param description descripción del permiso, se usa solo al insertarlo en el catálogo
     */
    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Devuelve el código del permiso
     *
     * @return código del permiso con el formato modulo:recurso:accion
     */
    public String getCode() {
        return code;
    }

    /**
     * Devuelve la descripción del permiso
     *
     * @return descripción del permiso
     */
    public String getDescription() {
        return description;
    }
}
