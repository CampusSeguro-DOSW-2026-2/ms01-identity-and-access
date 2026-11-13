package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * Convierte las cabeceras de identidad X-User-Id, X-User-Role y X-User-Permission en un RequesterIdentity
 *
 * Si faltan las cabeceras de identidad en un endpoint protegido se responde 401
 *
 * Los valores que no corresponden a algo valido se rechazan con 401
 */
public final class IdentityHeaderParser {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String USER_PERMISSION_HEADER = "X-User-Permission";

    public static final String MISSING_MESSAGE =
            "Faltan las cabeceras de identidad (X-User-Id / X-User-Role / X-User-Permission).";
    public static final String INVALID_USER_ID_MESSAGE = "La cabecera X-User-Id no es válida.";
    public static final String INVALID_ROLE_MESSAGE = "La cabecera X-User-Role no es válida.";
    public static final String INVALID_PERMISSION_MESSAGE = "La cabecera X-User-Permission no es válida.";

    private static final String LIST_SEPARATOR = ",";

    /**
     * No se instancia
     */
    private IdentityHeaderParser() {
    }

    /**
     * Construye la identidad del solicitante a partir de las cabeceras recibidas
     *
     * @param userIdHeader debe ser el UUID del usuario
     * @param roleHeader roles del usuario separados por coma
     * @param permissionHeader permisos del usuario separados por coma
     * @return la identidad del solicitante con su id, sus roles y sus permisos
     * @throws UnauthenticatedException si falta alguna cabecera o si su valor no es valido
     */
    public static RequesterIdentity parse(String userIdHeader, String roleHeader, String permissionHeader) {
        if (isBlank(userIdHeader) || isBlank(roleHeader) || isBlank(permissionHeader)) {
            throw new UnauthenticatedException(MISSING_MESSAGE);
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException(INVALID_USER_ID_MESSAGE);
        }

        Set<String> roles = new HashSet<>();
        for (String token : roleHeader.split(LIST_SEPARATOR)) {
            String name = token.trim();
            if (!name.isEmpty()) {
                if (!RoleNames.hasStoredFormat(name)) {
                    throw new UnauthenticatedException(INVALID_ROLE_MESSAGE);
                }
                roles.add(name);
            }
        }

        Set<String> permissions = new HashSet<>();
        for (String token : permissionHeader.split(LIST_SEPARATOR)) {
            String code = token.trim();
            if (!code.isEmpty()) {
                if (!PermissionCodes.hasFormat(code)) {
                    throw new UnauthenticatedException(INVALID_PERMISSION_MESSAGE);
                }
                permissions.add(code);
            }
        }

        if (roles.isEmpty() || permissions.isEmpty()) {
            throw new UnauthenticatedException(MISSING_MESSAGE);
        }
        return new RequesterIdentity(userId, roles, permissions);
    }

    /**
     * Verifica si un String es nulo o esta lleno de espacios o vacio
     *
     * @param value String a verificar
     * @return true si el valor es null o esta en blanco
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
