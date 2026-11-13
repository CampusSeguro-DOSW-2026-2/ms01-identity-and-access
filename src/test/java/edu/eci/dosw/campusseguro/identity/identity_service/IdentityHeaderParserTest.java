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
 * RN-MS01-23 / estándares de integración: la identidad de quien llama se toma de las cabeceras X-User-Id,
 * X-User-Role y X-User-Permission del API Gateway, si faltan o no son válidas la petición es 401. También cubre
 * la autorización por permisos de RequesterIdentity (MR-01, MR-07)
 */
class IdentityHeaderParserTest {

    private static final String USER_ID = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";
    private static final String PERMISSIONS = "ms01:profile:read,ms01:user:roles";

    /**
     * Una identidad con un solo rol y un permiso se interpreta correctamente
     */
    @Test
    void parse_singleRole_returnsTheIdentity() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "ADMIN", "ms01:user:roles");

        assertEquals(UUID.fromString(USER_ID), identity.userId());
        assertEquals(Set.of("ADMIN"), identity.roles());
        assertEquals(Set.of("ms01:user:roles"), identity.permissions());
    }

    /**
     * Varios roles y permisos separados por coma se interpretan todos, ignorando espacios y repetidos
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
     * Un rol personalizado con formato válido se acepta, el parser no consulta el catálogo de roles
     */
    @Test
    void parse_customRoleName_isAccepted() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "MONITOR_EVACUACION", PERMISSIONS);

        assertEquals(Set.of("MONITOR_EVACUACION"), identity.roles());
    }

    /**
     * Un código de permiso con formato válido se acepta aunque no esté en el catálogo del código, el catálogo
     * solo lo aplica el emisor del token
     */
    @Test
    void parse_permissionWithValidFormat_isAccepted() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "ADMIN", "ms09:futuro:permiso");

        assertEquals(Set.of("ms09:futuro:permiso"), identity.permissions());
    }

    /**
     * Una coma sobrante no invalida las cabeceras siempre que haya al menos un valor
     */
    @Test
    void parse_trailingComma_isTolerated() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "BRIGADISTA,", "ms05:panel:read_own,");

        assertEquals(Set.of("BRIGADISTA"), identity.roles());
        assertEquals(Set.of("ms05:panel:read_own"), identity.permissions());
    }

    /**
     * Sin X-User-Id la identidad no se puede establecer (401)
     */
    @Test
    void parse_missingUserId_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(null, "ADMIN", PERMISSIONS));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, error.getMessage());
    }

    /**
     * Sin X-User-Role la identidad no se puede establecer (401)
     */
    @Test
    void parse_missingRole_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, null, PERMISSIONS));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, error.getMessage());
    }

    /**
     * Sin X-User-Permission la identidad no se puede establecer (401), la autorización solo usa los permisos
     */
    @Test
    void parse_missingPermission_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, "ADMIN", null));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, error.getMessage());
    }

    /**
     * Las cabeceras en blanco cuentan como ausentes
     */
    @Test
    void parse_blankHeaders_throwUnauthenticated() {
        assertThrows(UnauthenticatedException.class, () -> IdentityHeaderParser.parse("   ", "ADMIN", PERMISSIONS));
        assertThrows(UnauthenticatedException.class, () -> IdentityHeaderParser.parse(USER_ID, "   ", PERMISSIONS));
        assertThrows(UnauthenticatedException.class, () -> IdentityHeaderParser.parse(USER_ID, "ADMIN", "   "));
    }

    /**
     * Una cabecera de roles o de permisos que solo tiene separadores no establece ningún valor (401)
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
     * Un X-User-Id que no es un UUID se rechaza (401)
     */
    @Test
    void parse_userIdWithoutUuidFormat_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse("USR-1034", "ADMIN", PERMISSIONS));

        assertEquals(IdentityHeaderParser.INVALID_USER_ID_MESSAGE, error.getMessage());
    }

    /**
     * Un nombre de rol con formato inválido se rechaza (401), aunque los demás sean válidos
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
     * Un código de permiso con formato inválido se rechaza (401), aunque los demás sean válidos
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
     * MR-01: los permisos del solicitante son los que trae X-User-Permission, la unión de los de sus roles
     */
    @Test
    void requesterPermissions_areTheOnesInTheHeader() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "PROFESOR,AUDITOR",
                String.join(",", TestIdentities.permissionsOf("PROFESOR", "AUDITOR")));

        assertTrue(identity.hasPermission(Permission.MS05_INCIDENT_REPORT));
        assertTrue(identity.hasPermission(Permission.MS01_USER_READ));
        assertFalse(identity.hasPermission(Permission.MS01_USER_ROLES));
    }

    /**
     * La autorización no depende del nombre del rol: ADMIN sin el permiso no puede cambiar roles y un rol
     * cualquiera con el permiso sí
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
     * Los roles y permisos de la identidad son conjuntos inmutables que no cambian si cambian los originales
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
