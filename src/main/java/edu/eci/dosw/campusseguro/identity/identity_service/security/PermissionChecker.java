package edu.eci.dosw.campusseguro.identity.identity_service.security;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;

/**
 * Checks that the requester has the permission that an operation requires according to the Role-Permission matrix
 * (Integration Standards, section 6), the authorization is always decided by the permission and never by the name of a
 * role
 */
public final class PermissionChecker {

        /**
     * Utility class, it is not instantiated
     */
    private PermissionChecker() {
    }

        /**
     * Requires that the requester has the permission, if it does not have it the operation is rejected with HTTP 403
     *
     * @param requester identity of whoever requests
     * @param permission permission that the operation requires
     * @throws ForbiddenOperationException if the requester does not have the permission in X-User-Permission
     */
    public static void require(RequesterIdentity requester, Permission permission) {
        if (!requester.hasPermission(permission)) {
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }
    }
}
