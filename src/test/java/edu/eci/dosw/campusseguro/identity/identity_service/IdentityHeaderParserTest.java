package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UnauthenticatedException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * RN-MS01-23 / estandares de integracion: la identidad de quien llama se toma de las cabeceras X-User-Id y
 * X-User-Role del API Gateway; si faltan o no son validas la peticion es 401. Tambien cubre la
 * autorizacion por permisos de RequesterIdentity (MR-01, MR-07).
 */
class IdentityHeaderParserTest {

    private static final String USER_ID = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";

    /**
     * Una identidad con un solo rol se interpreta correctamente.
     */
    @Test
    void parse_singleRole_returnsTheIdentity() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "ADMIN");

        assertEquals(UUID.fromString(USER_ID), identity.userId());
        assertEquals(EnumSet.of(UserRole.ADMIN), identity.roles());
    }

    /**
     * Varios roles separados por coma se interpretan todos, ignorando espacios y repetidos.
     */
    @Test
    void parse_severalRoles_ignoresSpacesAndDuplicates() {
        RequesterIdentity identity = IdentityHeaderParser.parse("  " + USER_ID + " ",
                "ESTUDIANTE, PROFESOR ,ESTUDIANTE");

        assertEquals(UUID.fromString(USER_ID), identity.userId());
        assertEquals(EnumSet.of(UserRole.ESTUDIANTE, UserRole.PROFESOR), identity.roles());
    }

    /**
     * Una coma sobrante no invalida la cabecera siempre que haya al menos un rol.
     */
    @Test
    void parse_trailingComma_isTolerated() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "BRIGADISTA,");

        assertEquals(EnumSet.of(UserRole.BRIGADISTA), identity.roles());
    }

    /**
     * Sin X-User-Id la identidad no se puede establecer (401).
     */
    @Test
    void parse_missingUserId_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(null, "ADMIN"));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, error.getMessage());
    }

    /**
     * Sin X-User-Role la identidad no se puede establecer (401).
     */
    @Test
    void parse_missingRole_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, null));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, error.getMessage());
    }

    /**
     * Las cabeceras en blanco cuentan como ausentes.
     */
    @Test
    void parse_blankHeaders_throwUnauthenticated() {
        assertThrows(UnauthenticatedException.class, () -> IdentityHeaderParser.parse("   ", "ADMIN"));
        assertThrows(UnauthenticatedException.class, () -> IdentityHeaderParser.parse(USER_ID, "   "));
    }

    /**
     * Una cabecera de roles que solo tiene separadores no establece ningun rol (401).
     */
    @Test
    void parse_onlySeparators_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, " , ,"));

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE, error.getMessage());
    }

    /**
     * Un X-User-Id que no es un UUID se rechaza (401).
     */
    @Test
    void parse_userIdWithoutUuidFormat_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse("USR-1034", "ADMIN"));

        assertEquals(IdentityHeaderParser.INVALID_USER_ID_MESSAGE, error.getMessage());
    }

    /**
     * Un rol fuera del catalogo se rechaza (401), aunque los demas sean validos.
     */
    @Test
    void parse_roleOutsideTheCatalog_throwsUnauthenticated() {
        UnauthenticatedException error = assertThrows(UnauthenticatedException.class,
                () -> IdentityHeaderParser.parse(USER_ID, "ESTUDIANTE,SUPERUSUARIO"));

        assertEquals(IdentityHeaderParser.INVALID_ROLE_MESSAGE, error.getMessage());
    }

    /**
     * Los nombres de rol van en mayusculas; en minusculas no pertenecen al catalogo (401).
     */
    @Test
    void parse_lowercaseRole_throwsUnauthenticated() {
        assertThrows(UnauthenticatedException.class, () -> IdentityHeaderParser.parse(USER_ID, "admin"));
    }

    /**
     * MR-01: los permisos del solicitante son la union de los permisos de todos sus roles.
     */
    @Test
    void requesterPermissions_areTheUnionOfItsRoles() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER_ID, "PROFESOR,AUDITOR");

        assertTrue(identity.hasPermission(Permission.MS05_INCIDENT_REPORT));
        assertTrue(identity.hasPermission(Permission.MS01_USER_READ));
        assertFalse(identity.hasPermission(Permission.MS01_USER_ROLES));
    }

    /**
     * Solo quien tiene el rol ADMIN tiene el permiso para cambiar roles.
     */
    @Test
    void onlyAdminHasTheChangeRolesPermission() {
        assertTrue(IdentityHeaderParser.parse(USER_ID, "ADMIN").hasPermission(Permission.MS01_USER_ROLES));
        assertTrue(IdentityHeaderParser.parse(USER_ID, "ESTUDIANTE,ADMIN")
                .hasPermission(Permission.MS01_USER_ROLES));
        assertFalse(IdentityHeaderParser.parse(USER_ID, "COORDINADOR,AUDITOR")
                .hasPermission(Permission.MS01_USER_ROLES));
    }

    /**
     * Los roles de la identidad son un conjunto inmutable que no cambia si cambia el original.
     */
    @Test
    void requesterIdentity_rolesAreAnImmutableCopy() {
        EnumSet<UserRole> original = EnumSet.of(UserRole.ESTUDIANTE);
        RequesterIdentity identity = new RequesterIdentity(UUID.fromString(USER_ID), original);

        original.add(UserRole.ADMIN);

        assertEquals(EnumSet.of(UserRole.ESTUDIANTE), identity.roles());
        assertThrows(UnsupportedOperationException.class, () -> identity.roles().add(UserRole.ADMIN));
    }
}
