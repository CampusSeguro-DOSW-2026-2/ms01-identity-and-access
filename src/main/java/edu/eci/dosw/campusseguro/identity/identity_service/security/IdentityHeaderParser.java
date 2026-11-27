package edu.eci.dosw.campusseguro.identity.identity_service.security;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * Converts the identity headers X-User-Id, X-User-Role and X-User-Permission into a RequesterIdentity
 *
 * If the identity headers are missing on a protected endpoint, 401 is answered
 *
 * The values that do not correspond to something valid are rejected with 401
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
     * It is not instantiated
     */
    private IdentityHeaderParser() {
    }

        /**
     * Builds the identity of the requester from the received headers
     *
     * @param userIdHeader must be the UUID of the user
     * @param roleHeader roles of the user separated by comma
     * @param permissionHeader permissions of the user separated by comma
     * @return the identity of the requester with its id, its roles and its permissions
     * @throws UnauthenticatedException if any header is missing or if its value is not valid
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
     * Checks whether a String is null, blank or empty
     *
     * @param value String a verificar
     * @return true if the value is null or blank
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
