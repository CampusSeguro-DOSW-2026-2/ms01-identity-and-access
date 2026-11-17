package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;

/**
 * MS01 permissions defined by the code (RN-MS01-14): 10 codes with the format module:resource:action, without repeated
 * ones and with a description
 */
class PermissionTest {

        /**
     * The code defines only the 10 permissions of MS01
     */
    @Test
    void enum_hasTheTenMs01Permissions() {
        assertEquals(10, Permission.values().length);
    }

        /**
     * All the codes have the format module:resource:action, none is repeated and all belong to MS01
     */
    @Test
    void everyCode_hasTheFormatIsUniqueAndBelongsToMs01() {
        Set<String> codes = new HashSet<>();
        for (Permission permission : Permission.values()) {
            assertTrue(PermissionCodes.hasFormat(permission.getCode()), permission.getCode());
            assertTrue(permission.getCode().startsWith(Permission.PREFIX), permission.getCode());
            assertTrue(codes.add(permission.getCode()), permission.getCode());
        }
    }

        /**
     * All the permissions have a description with content
     */
    @Test
    void everyPermission_hasADescription() {
        for (Permission permission : Permission.values()) {
            assertFalse(permission.getDescription().isBlank(), permission.getCode());
        }
    }

        /**
     * The prefix of the enum is the one of the MS01 module
     */
    @Test
    void prefix_isTheMs01Module() {
        assertEquals("ms01:", Permission.PREFIX);
    }

        /**
     * The role management permission and the complete query one exist with the code of the contract
     */
    @Test
    void roleManagementPermissions_haveTheContractCodes() {
        assertEquals("ms01:role:manage", Permission.MS01_ROLE_MANAGE.getCode());
        assertEquals("ms01:role:read_all", Permission.MS01_ROLE_READ_ALL.getCode());
        assertEquals("ms01:role:read_own", Permission.MS01_ROLE_READ_OWN.getCode());
        assertEquals("ms01:user:roles", Permission.MS01_USER_ROLES.getCode());
    }

        /**
     * The profile permissions exist with the code of the contract
     */
    @Test
    void profilePermissions_haveTheContractCodes() {
        assertEquals("ms01:profile:read", Permission.MS01_PROFILE_READ.getCode());
        assertEquals("ms01:profile:write", Permission.MS01_PROFILE_WRITE.getCode());
    }
}
