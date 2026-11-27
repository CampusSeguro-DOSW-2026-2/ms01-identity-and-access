package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;

/**
 * Format of the permission codes and computation of the permissions of a role and of a user from the stored roles
 */
class PermissionCodesTest {

        /**
     * Stored role with the indicated permissions
     *
     * @param name name of the role
     * @param permissions stored permissions
     * @return the entity
     */
    private RoleEntity role(String name, String... permissions) {
        return TestEntities.role(name, permissions);
    }

        /**
     * Codes with the format module:resource:action
     *
     * @param code valid code
     */
    @ParameterizedTest
    @ValueSource(strings = {"ms01:role:manage", "ms04:checkin:read_own", "ms99:a:b", "ms00:x1:y2"})
    void hasFormat_validCodes_areAccepted(String code) {
        assertTrue(PermissionCodes.hasFormat(code));
    }

        /**
     * Codes that do not have the format
     *
     * @param code invalid code
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "ms1:role:manage", "MS01:role:manage", "ms01:Role:manage", "ms01:role",
            "ms01:role:manage:x", "ms01:1role:manage", "ms01:role:_manage", " ms01:role:manage", "ms01:role-x:y",
            "ms01::manage"})
    void hasFormat_invalidCodes_areRejected(String code) {
        assertFalse(PermissionCodes.hasFormat(code));
    }

        /**
     * A null code does not have the format
     */
    @Test
    void hasFormat_null_isRejected() {
        assertFalse(PermissionCodes.hasFormat(null));
    }

        /**
     * The permissions of a role come out sorted and without repeated ones
     */
    @Test
    void ofRole_sortsAndDeduplicates() {
        RoleEntity role = role("MONITOR", "ms04:checkin:write", "ms01:profile:read", "ms04:checkin:write");

        assertEquals(List.of("ms01:profile:read", "ms04:checkin:write"), PermissionCodes.ofRole(role));
    }

        /**
     * The permissions of the role are returned as they are in the database, without changing case
     */
    @Test
    void ofRole_returnsTheStoredCodesAsTheyAre() {
        RoleEntity role = role("MIXTO", "ms01:profile:read", "ms99:x:y");

        assertEquals(List.of("ms01:profile:read", "ms99:x:y"), PermissionCodes.ofRole(role));
    }

        /**
     * A role without permissions returns an empty list
     */
    @Test
    void ofRole_withoutPermissions_isEmpty() {
        assertTrue(PermissionCodes.ofRole(role("VACIO")).isEmpty());
    }

        /**
     * The effective permissions are the union of those of all the roles, sorted and without repeated ones
     */
    @Test
    void effective_isTheUnionOfTheRolesPermissions() {
        List<String> effective = PermissionCodes.effective(List.of(
                role("A", "ms04:checkin:write", "ms01:profile:read"),
                role("B", "ms01:profile:read", "ms03:alert:receive")));

        assertEquals(List.of("ms01:profile:read", "ms03:alert:receive", "ms04:checkin:write"), effective);
    }

        /**
     * Without roles there are no effective permissions
     */
    @Test
    void effective_withoutRoles_isEmpty() {
        assertTrue(PermissionCodes.effective(List.of()).isEmpty());
    }

        /**
     * A role repeated among the roles of the user does not repeat its permissions
     */
    @Test
    void effective_sameRoleTwice_doesNotRepeatPermissions() {
        RoleEntity role = role("A", "ms01:profile:read", "ms04:checkin:write");

        assertEquals(List.of("ms01:profile:read", "ms04:checkin:write"),
                PermissionCodes.effective(List.of(role, role)));
    }
}
