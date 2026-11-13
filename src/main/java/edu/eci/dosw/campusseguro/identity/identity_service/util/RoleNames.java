package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nombres de rol de CampusSeguro: los dos roles de sistema que el código necesita conocer, el nombre
 * reservado, el formato de un nombre guardado y la normalización de un nombre recibido (RG-08)
 */
public final class RoleNames {

    public static final String STUDENT = "ESTUDIANTE";
    public static final String ADMIN = "ADMIN";
    public static final String RESERVED = "DETAILS";

    private static final Pattern STORED_FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    private static final Pattern ENTRY_FORMAT = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{1,29}$");

    /**
     * Clase de utilidad, no se instancia
     */
    private RoleNames() {
    }

    /**
     * Indica si un nombre tiene el formato de un rol guardado, el mismo de la tabla roles y de la cabecera
     * X-User-Role: mayúsculas, dígitos y guion bajo, de 2 a 30 caracteres y empezando con una letra
     *
     * @param name nombre a comprobar, puede ser null
     * @return true si el nombre es un nombre de rol con formato válido, sin importar si existe
     */
    public static boolean hasStoredFormat(String name) {
        return name != null && STORED_FORMAT.matcher(name).matches();
    }

    /**
     * Indica si un nombre está reservado y no puede darse a un rol, es el segmento fijo de la ruta de
     * consulta del detalle de los roles
     *
     * @param name nombre ya normalizado, puede ser null
     * @return true si el nombre es el reservado
     */
    public static boolean isReserved(String name) {
        return RESERVED.equals(name);
    }

    /**
     * Normaliza el nombre de un rol recibido en una ruta o en un cuerpo (RG-08): recorta los espacios, valida el
     * formato de entrada y lo pasa a mayúsculas
     *
     * @param raw nombre recibido, puede ser null
     * @return el nombre en mayúsculas, o null si es null o no tiene un formato válido
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!ENTRY_FORMAT.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * Ordena nombres de rol alfabéticamente
     *
     * @param roles nombres de rol
     * @return lista nueva con los nombres en orden alfabético
     */
    public static List<String> sorted(Collection<String> roles) {
        return roles.stream()
                .sorted()
                .toList();
    }
}
