package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;

/**
 * Convierte las cabeceras de identidad X-User-Id y X-User-Role en un RequesterIdentity
 *
 * Si faltan las cabeceras de identidad en un endpoint protegido se responde 401
 * 
 * Los valores que no corresponden a algo valido se rechazan con 401
 */
public final class IdentityHeaderParser {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    public static final String MISSING_MESSAGE = "Faltan las cabeceras de identidad (X-User-Id / X-User-Role).";
    public static final String INVALID_USER_ID_MESSAGE = "La cabecera X-User-Id no es válida.";
    public static final String INVALID_ROLE_MESSAGE = "La cabecera X-User-Role no es válida.";

    private static final String ROLE_SEPARATOR = ",";

    /**
     * 
     */
    private IdentityHeaderParser() {
    }

    /**
     * Construye la identidad del solicitante a partir de las cabeceras recibidas
     *
     * @param userIdHeader debe ser el UUID del usuario
     * @param roleHeader roles del usuario separados por coma
     * @return la identidad del solicitante con su id y su conjunto de roles
     * @throws UnauthenticatedException si falta alguna cabecera o si su valor no es valido
     */
    public static RequesterIdentity parse(String userIdHeader, String roleHeader) {
        if (isBlank(userIdHeader) || isBlank(roleHeader)) {
            throw new UnauthenticatedException(MISSING_MESSAGE);
        }
        UUID userId = parseUserId(userIdHeader);
        Set<UserRole> roles = parseRoles(roleHeader);
        return new RequesterIdentity(userId, roles);
    }

    /**
     * Convierte el valor de X-User-Id en un UUID
     *
     * @param userIdHeader valor de la cabecera
     * @return el identificador del usuario
     * @throws UnauthenticatedException si el valor no tiene formato UUID
     */
    private static UUID parseUserId(String userIdHeader) {
        try {
            return UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException(INVALID_USER_ID_MESSAGE);
        }
    }

    /**
     * Convierte el valor de X-User-Role en el conjunto de roles del usuario
     *
     * @param roleHeader roles del usuario separados por coma
     * @return conjunto de roles
     * @throws UnauthenticatedException si algun rol no existe en el codigo o si no hay ningun rol
     */
    private static Set<UserRole> parseRoles(String roleHeader) {
        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (String token : roleHeader.split(ROLE_SEPARATOR)) {
            String name = token.trim();
            if (!name.isEmpty()) {
                roles.add(parseRole(name));
            }
        }
        if (roles.isEmpty()) {
            throw new UnauthenticatedException(MISSING_MESSAGE);
        }
        return roles;
    }

    /**
     * Convierte el nombre de un rol en su enum
     *
     * @param name nombre del rol en mayusculas
     * @return el rol convertido en enum
     * @throws UnauthenticatedException si el nombre del rol no esta en el enum
     */
    private static UserRole parseRole(String name) {
        try {
            return UserRole.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException(INVALID_ROLE_MESSAGE);
        }
    }

    /**
     * Verifica si un String es nulo o esta lleno de espacios o vacio.
     *
     * @param value String a verificar
     * @return true si el valor es null o esta en blanco
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
