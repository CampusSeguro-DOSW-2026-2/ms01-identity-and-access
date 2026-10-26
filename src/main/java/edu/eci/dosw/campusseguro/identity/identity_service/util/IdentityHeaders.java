package edu.eci.dosw.campusseguro.identity.identity_service.util;

import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.NotAuthenticatedException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.PermissionDeniedException;

/**
 * Lectura y validacion de las cabeceras de identidad que inyecta el API Gateway (RN-MS01-23,
 * Estandares seccion 4): X-User-Id (UUID) y X-User-Role (roles separados por coma).
 */
public final class IdentityHeaders {

    private IdentityHeaders() {
    }

    /** Devuelve el id del usuario o lanza 401 si falta o no es un UUID. */
    public static UUID requireUserId(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new NotAuthenticatedException();
        }
        try {
            return UUID.fromString(rawUserId.trim());
        } catch (IllegalArgumentException e) {
            throw new NotAuthenticatedException();
        }
    }

    /**
     * Lanza 401 si X-User-Role falta y 403 si ninguno de sus roles esta entre los permitidos para la
     * operacion. Los valores que no pertenecen al catalogo de roles se ignoran.
     */
    public static void requireAnyRole(String rawRoles, Set<UserRole> allowed) {
        if (rawRoles == null || rawRoles.isBlank()) {
            throw new NotAuthenticatedException();
        }
        for (String token : rawRoles.split(",")) {
            try {
                if (allowed.contains(UserRole.valueOf(token.trim()))) {
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // Rol fuera del catalogo: no concede ningun permiso
            }
        }
        throw new PermissionDeniedException();
    }
}
