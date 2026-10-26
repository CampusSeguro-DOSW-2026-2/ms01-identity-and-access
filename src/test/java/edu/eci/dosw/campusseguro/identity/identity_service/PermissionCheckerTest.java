package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * La autorización se decide por permisos y nunca por nombres de rol (Estándares de Seguridad y Contratos de
 * Integración)
 */
class PermissionCheckerTest {

    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");

    /**
     * Un solicitante que tiene el permiso pasa la verificación
     */
    @Test
    void require_withThePermission_doesNothing() {
        RequesterIdentity requester = new RequesterIdentity(USER_ID, Set.of(UserRole.ADMIN));

        assertDoesNotThrow(() -> PermissionChecker.require(requester, Permission.MS01_USER_STATUS));
    }

    /**
     * Un solicitante sin el permiso recibe el mensaje del catálogo de errores
     */
    @Test
    void require_withoutThePermission_throwsForbiddenWithTheCatalogMessage() {
        RequesterIdentity requester = new RequesterIdentity(USER_ID, Set.of(UserRole.ESTUDIANTE));

        ForbiddenOperationException exception = assertThrows(ForbiddenOperationException.class,
                () -> PermissionChecker.require(requester, Permission.MS01_USER_STATUS));

        assertEquals(ErrorCode.FORBIDDEN.getMessage(), exception.getMessage());
    }

    /**
     * Con varios roles basta que uno de ellos otorgue el permiso
     */
    @Test
    void require_withSeveralRoles_acceptsThePermissionOfAnyOfThem() {
        RequesterIdentity requester = new RequesterIdentity(USER_ID,
                Set.of(UserRole.ESTUDIANTE, UserRole.ADMIN));

        assertDoesNotThrow(() -> PermissionChecker.require(requester, Permission.MS01_USER_ROLES));
    }
}
