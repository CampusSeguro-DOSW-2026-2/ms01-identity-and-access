package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
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
        RequesterIdentity requester = TestIdentities.of(USER_ID, "ADMIN");

        assertDoesNotThrow(() -> PermissionChecker.require(requester, Permission.MS01_USER_STATUS));
    }

    /**
     * Un solicitante sin el permiso recibe el mensaje del catálogo de errores
     */
    @Test
    void require_withoutThePermission_throwsForbiddenWithTheCatalogMessage() {
        RequesterIdentity requester = TestIdentities.of(USER_ID, "ESTUDIANTE");

        ForbiddenOperationException exception = assertThrows(ForbiddenOperationException.class,
                () -> PermissionChecker.require(requester, Permission.MS01_USER_STATUS));

        assertEquals(ErrorCode.FORBIDDEN.getMessage(), exception.getMessage());
    }

    /**
     * El permiso decide y no el nombre del rol: ADMIN sin el permiso es rechazado y un rol personalizado con el
     * permiso pasa
     */
    @Test
    void require_dependsOnThePermissionAndNotOnTheRoleName() {
        RequesterIdentity adminWithoutPermission = TestIdentities.withPermissions(USER_ID, Set.of("ADMIN"),
                "ms01:user:read");
        RequesterIdentity customWithPermission = TestIdentities.withPermissions(USER_ID, Set.of("SOPORTE"),
                "ms01:role:manage");

        assertThrows(ForbiddenOperationException.class,
                () -> PermissionChecker.require(adminWithoutPermission, Permission.MS01_ROLE_MANAGE));
        assertDoesNotThrow(() -> PermissionChecker.require(customWithPermission, Permission.MS01_ROLE_MANAGE));
    }

    /**
     * Con varios roles basta que uno de ellos otorgue el permiso
     */
    @Test
    void require_withSeveralRoles_acceptsThePermissionOfAnyOfThem() {
        RequesterIdentity requester = TestIdentities.of(USER_ID, "ESTUDIANTE", "ADMIN");

        assertDoesNotThrow(() -> PermissionChecker.require(requester, Permission.MS01_USER_ROLES));
    }
}
