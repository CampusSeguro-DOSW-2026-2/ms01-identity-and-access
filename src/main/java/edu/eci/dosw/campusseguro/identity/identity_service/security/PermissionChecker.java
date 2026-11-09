package edu.eci.dosw.campusseguro.identity.identity_service.security;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;

/**
 * Comprueba que el solicitante tenga el permiso que exige una operación según la matriz Rol-Permiso
 * (Estándares de Integración, sección 6), la autorización la decide siempre el permiso y nunca el nombre de
 * un rol
 */
public final class PermissionChecker {

    /**
     * Clase de utilidad, no se instancia
     */
    private PermissionChecker() {
    }

    /**
     * Exige que el solicitante tenga el permiso, si no lo tiene la operación se rechaza con HTTP 403
     *
     * @param requester identidad de quien solicita
     * @param permission permiso que exige la operación
     * @throws ForbiddenOperationException si ninguno de los roles del solicitante concede el permiso
     */
    public static void require(RequesterIdentity requester, Permission permission) {
        if (!requester.hasPermission(permission)) {
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }
    }
}
