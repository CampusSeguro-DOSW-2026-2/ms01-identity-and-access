package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * RN-MS01-23 / integration standards: the identity of the caller is taken from the headers X-User-Id, X-User-Role and
 * X-User-Permission of the API Gateway, if they are missing or not valid the request is 401. It also covers the
 * authorization by permissions of RequesterIdentity (MR-01, MR-07)
 */
class IdentityHeaderParserTest {

    private static final String USER_ID = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";
    private static final String PERMISSIONS = "ms01:profile:read,ms01:user:roles";

        /**
     * An identity with a single role and a permission is interpreted correctly
     */
    @Test
    void parse_singleRole_returnsTheIdentity() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "ADMIN", "ms01:user:roles");

        assertEquals(UUID.fromString(USER_ID), identity.userId());
        assertEquals(Set.of("ADMIN"), identity.roles());
        assertEquals(Set.of("ms01:user:roles"), identity.permissions());
    }

        /**
     * Several roles and permissions separated by comma are all interpreted, ignoring spaces and repeated ones
     */
    @Test
    void parse_severalValues_ignoresSpacesAndDuplicates() {
        RequesterIdentity identity = IdentityHeaderParser.parse("  " + USER_ID + " ",
                "ESTUDIANTE, PROFESOR ,ESTUDIANTE", " ms01:profile:read , ms05:incident:report,ms01:profile:read");

        assertEquals(UUID.fromString(USER_ID), identity.userId());
        assertEquals(Set.of("ESTUDIANTE", "PROFESOR"), identity.roles());
        assertEquals(Set.of("ms01:profile:read", "ms05:incident:report"), identity.permissions());
    }

        /**
     * A custom role with a valid format is accepted, the parser does not query the role catalog
     */
    @Test
    void parse_customRoleName_isAccepted() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "MONITOR_EVACUACION", PERMISSIONS);

        assertEquals(Set.of("MONITOR_EVACUACION"), identity.roles());
    }

        /**
     * A permission code with a valid format is accepted even if it is not in the catalog of the code, the catalog is
     * only applied by the issuer of the token
     */
    @Test
    void parse_permissionWithValidFormat_isAccepted() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "ADMIN", "ms09:futuro:permiso");

        assertEquals(Set.of("ms09:futuro:permiso"), identity.permissions());
    }

        /**
     * A leftover comma does not invalidate the headers as long as there is at least one value
     */
    @Test
    void parse_trailingComma_isTolerated() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "BRIGADISTA,", "ms05:panel:read_own,");

        assertEquals(Set.of("BRIGADISTA"), identity.roles());
        assertEquals(Set.of("ms05:panel:read_own"), identity.permissions());
    }

        /**
     * Without X-User-Id the identity cannot be established (401)
     */
    @Test
    void parse_missingUserId_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(null, "ADMIN", PERMISSIONS));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, error.getMessage());
    }

        /**
     * Without X-User-Role the identity cannot be established (401)
     */
    @Test
    void parse_missingRole_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, null, PERMISSIONS));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, error.getMessage());
    }

        /**
     * Without X-User-Permission the identity cannot be established (401), the authorization only uses the permissions
     */
    @Test
    void parse_missingPermission_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, "ADMIN", null));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, error.getMessage());
    }

        /**
     * Blank headers count as absent
     */
    @Test
    void parse_blankHeaders_throwUnauthenticated() {
        assertThrows(UnauthenticatedException.class, () -> IdentityHeaderParser.parse("   ", "ADMIN", PERMISSIONS));
        assertThrows(UnauthenticatedException.class, () -> IdentityHeaderParser.parse(USER_ID, "   ", PERMISSIONS));
        assertThrows(UnauthenticatedException.class, () -> IdentityHeaderParser.parse(USER_ID, "ADMIN", "   "));
    }

        /**
     * A roles or permissions header that only has separators does not establish any value (401)
     */
    @Test
    void parse_onlySeparators_throwsUnauthenticated() {
        UnauthenticatedException roles = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, " , ,", PERMISSIONS));
        UnauthenticatedException permissions = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, "ADMIN", " , ,"));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, roles.getMessage());
        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, permissions.getMessage());
    }

        /**
     * An X-User-Id that is not a UUID is rejected (401)
     */
    @Test
    void parse_userIdWithoutUuidFormat_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse("USR-1034", "ADMIN", PERMISSIONS));

        assertEquals(IdentityHeaderParser.INVALID_USER_ID_MESSAGE, error.getMessage());
    }

        /**
     * A role name with an invalid format is rejected (401), even if the others are valid
     */
    @ParameterizedTest
    @ValueSource(strings = {"ESTUDIANTE,SUPER-USUARIO", "ESTUDIANTE,1ROL", "ESTUDIANTE,R", "admin", "Admin",
            "ESTUDIANTE,ROL CON ESPACIOS", "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345"})
    void parse_roleWithInvalidFormat_throwsUnauthenticated(String roles) {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, roles, PERMISSIONS));

        assertEquals(IdentityHeaderParser.INVALID_ROLE_MESSAGE, error.getMessage());
    }

        /**
     * A permission code with an invalid format is rejected (401), even if the others are valid
     */
    @ParameterizedTest
    @ValueSource(strings = {"ms01:profile:read,PERMISO", "ms01:profile", "MS01:profile:read", "ms1:profile:read",
            "ms01:Profile:read", "ms01:profile:read:extra", "ms01:1perfil:read", "ms01:profile:read-all"})
    void parse_permissionWithInvalidFormat_throwsUnauthenticated(String permissions) {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, "ADMIN", permissions));

        assertEquals(IdentityHeaderParser.INVALID_PERMISSION_MESSAGE, error.getMessage());
    }

        /**
     * MR-01: the permissions of the requester are those carried by X-User-Permission, the union of those of their roles
     */
    @Test
    void requesterPermissions_areTheOnesInTheHeader() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "PROFESOR,AUDITOR",
                String.join(",", TestIdentities.permissionsOf("PROFESOR", "AUDITOR")));

        assertTrue(identity.permissions().contains("ms05:incident:report"));
        assertTrue(identity.hasPermission(Permission.MS01_USER_READ));
        assertFalse(identity.hasPermission(Permission.MS01_USER_ROLES));
    }

        /**
     * The authorization does not depend on the name of the role: ADMIN without the permission cannot change roles and
     * any role with the permission can
     */
    @Test
    void authorization_dependsOnThePermissionAndNotOnTheRoleName() {
        RequesterIdentity adminWithoutPermission = IdentityHeaderParser.parse(USER_ID, "ADMIN", "ms01:user:read");
        RequesterIdentity supportWithPermission = IdentityHeaderParser.parse(USER_ID, "SOPORTE",
                "ms01:user:roles");

        assertFalse(adminWithoutPermission.hasPermission(Permission.MS01_USER_ROLES));
        assertTrue(supportWithPermission.hasPermission(Permission.MS01_USER_ROLES));
    }

        /**
     * The roles and permissions of the identity are immutable sets that do not change if the originals change
     */
    @Test
    void requesterIdentity_valuesAreAnImmutableCopy() {
        Set<String> roles = new HashSet<>(Set.of("ESTUDIANTE"));
        Set<String> permissions = new HashSet<>(Set.of("ms01:profile:read"));
        RequesterIdentity identity = new RequesterIdentity(UUID.fromString(USER_ID), roles, permissions);

        roles.add("ADMIN");
        permissions.add("ms01:user:roles");

        assertEquals(Set.of("ESTUDIANTE"), identity.roles());
        assertEquals(Set.of("ms01:profile:read"), identity.permissions());
        assertThrows(UnsupportedOperationException.class, () -> identity.roles().add("ADMIN"));
        assertThrows(UnsupportedOperationException.class, () -> identity.permissions().add("ms01:user:roles"));
    }
}
