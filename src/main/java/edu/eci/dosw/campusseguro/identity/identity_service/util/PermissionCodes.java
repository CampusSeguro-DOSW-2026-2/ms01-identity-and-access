package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;

/**
 * Códigos de permiso: su formato y el cálculo de los permisos efectivos de un usuario a partir de los roles
 * guardados
 */
public final class PermissionCodes {

    private static final Pattern FORMAT = Pattern.compile("^ms[0-9]{2}:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$");

    /**
     * Clase de utilidad, no se instancia
     */
    private PermissionCodes() {
    }

    /**
     * Indica si un código tiene el formato modulo:recurso:accion, sin comprobar que esté en el catálogo
     *
     * @param code código a comprobar, puede ser null
     * @return true si el código tiene el formato de un permiso
     */
    public static boolean hasFormat(String code) {
        return code != null && FORMAT.matcher(code).matches();
    }

    /**
     * Devuelve los permisos de un rol tal como se muestran y se usan: los códigos de sus filas de rol_permiso,
     * sin repetidos y en orden alfabético
     *
     * @param role rol, tal como está guardado
     * @return los códigos de permiso del rol
     */
    public static List<String> ofRole(RoleEntity role) {
        TreeSet<String> codes = new TreeSet<>();
        for (PermissionEntity permission : role.getPermissions()) {
            codes.add(permission.getName());
        }
        return List.copyOf(codes);
    }

    /**
     * Calcula los permisos efectivos de un usuario: la unión de los permisos de sus roles
     *
     * @param roles roles del usuario, tal como están guardados
     * @return los códigos de permiso en orden alfabético y sin repetidos
     */
    public static List<String> effective(Collection<RoleEntity> roles) {
        TreeSet<String> codes = new TreeSet<>();
        for (RoleEntity role : roles) {
            codes.addAll(ofRole(role));
        }
        return List.copyOf(codes);
    }
}
