package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Identidad del solicitante reconstruida desde las cabeceras que inyecta el API Gateway
 */
class RequesterIdentityTest {

    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");

    /**
     * El identificador y los roles son obligatorios
     */
    @Test
    void constructor_rejectsNullValues() {
        assertThrows(NullPointerException.class, () -> new RequesterIdentity(null, Set.of(UserRole.ADMIN)));
        assertThrows(NullPointerException.class, () -> new RequesterIdentity(USER_ID, null));
    }

    /**
     * Los roles se copian y no se pueden modificar desde fuera
     */
    @Test
    void roles_areAnUnmodifiableCopy() {
        Set<UserRole> source = EnumSet.of(UserRole.ESTUDIANTE);
        RequesterIdentity requester = new RequesterIdentity(USER_ID, source);

        source.add(UserRole.ADMIN);

        assertEquals(Set.of(UserRole.ESTUDIANTE), requester.roles());
        assertThrows(UnsupportedOperationException.class, () -> requester.roles().add(UserRole.ADMIN));
    }

    /**
     * Los permisos son la unión de los permisos de todos sus roles
     */
    @Test
    void permissions_areTheUnionOfTheRolePermissions() {
        RequesterIdentity requester = new RequesterIdentity(USER_ID,
                Set.of(UserRole.ESTUDIANTE, UserRole.AUDITOR));

        Set<Permission> expected = EnumSet.noneOf(Permission.class);
        expected.addAll(UserRole.ESTUDIANTE.getPermissions());
        expected.addAll(UserRole.AUDITOR.getPermissions());

        assertEquals(expected, requester.permissions());
    }

    /**
     * La verificación de un permiso se resuelve con los permisos de los roles
     */
    @Test
    void hasPermission_followsTheRolePermissions() {
        RequesterIdentity requester = new RequesterIdentity(USER_ID, Set.of(UserRole.ESTUDIANTE));

        assertTrue(requester.hasPermission(Permission.MS01_PROFILE_READ));
        assertFalse(requester.hasPermission(Permission.MS01_USER_STATUS));
    }

    /**
     * La cabecera de roles va separada por comas y en orden alfabético
     */
    @Test
    void rolesHeader_isCommaSeparatedAndAlphabetical() {
        RequesterIdentity requester = new RequesterIdentity(USER_ID,
                Set.of(UserRole.PROFESOR, UserRole.ADMIN, UserRole.BRIGADISTA));

        assertEquals("ADMIN,BRIGADISTA,PROFESOR", requester.rolesHeader());
    }
}
