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
 * The authorization is decided by permissions and never by role names (Security Standards and Integration Contracts)
 */
class PermissionCheckerTest {

    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");

        /**
     * A requester that has the permission passes the verification
     */
    @Test
    void require_withThePermission_doesNothing() {
        RequesterIdentity requester = TestIdentities.of(USER_ID, "ADMIN");

        assertDoesNotThrow(() -> PermissionChecker.require(requester, Permission.MS01_USER_STATUS));
    }

        /**
     * A requester without the permission receives the message of the error catalog
     */
    @Test
    void require_withoutThePermission_throwsForbiddenWithTheCatalogMessage() {
        RequesterIdentity requester = TestIdentities.of(USER_ID, "ESTUDIANTE");

        ForbiddenOperationException exception = assertThrows(ForbiddenOperationException.class,
                () -> PermissionChecker.require(requester, Permission.MS01_USER_STATUS));

        assertEquals(ErrorCode.FORBIDDEN.getMessage(), exception.getMessage());
    }

        /**
     * The permission decides and not the name of the role: ADMIN without the permission is rejected and a custom role
     * with the permission passes
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
     * With several roles it is enough that one of them grants the permission
     */
    @Test
    void require_withSeveralRoles_acceptsThePermissionOfAnyOfThem() {
        RequesterIdentity requester = TestIdentities.of(USER_ID, "ESTUDIANTE", "ADMIN");

        assertDoesNotThrow(() -> PermissionChecker.require(requester, Permission.MS01_USER_ROLES));
    }
}
