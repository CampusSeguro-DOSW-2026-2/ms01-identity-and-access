package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Identidad del solicitante reconstruida desde las cabeceras que inyecta el API Gateway
 */
class RequesterIdentityTest {

    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");

    /**
     * El identificador, los roles y los permisos son obligatorios
     */
    @Test
    void constructor_rejectsNullValues() {
        assertThrows(NullPointerException.class, () -> new RequesterIdentity(null, Set.of("ADMIN"), Set.of()));
        assertThrows(NullPointerException.class, () -> new RequesterIdentity(USER_ID, null, Set.of()));
        assertThrows(NullPointerException.class, () -> new RequesterIdentity(USER_ID, Set.of("ADMIN"), null));
    }

    /**
     * Los roles y los permisos se copian y no se pueden modificar desde fuera
     */
    @Test
    void rolesAndPermissions_areAnUnmodifiableCopy() {
        Set<String> roles = new HashSet<>(Set.of("ESTUDIANTE"));
        Set<String> permissions = new HashSet<>(Set.of("ms01:profile:read"));
        RequesterIdentity requester = new RequesterIdentity(USER_ID, roles, permissions);

        roles.add("ADMIN");
        permissions.add("ms01:user:roles");

        assertEquals(Set.of("ESTUDIANTE"), requester.roles());
        assertEquals(Set.of("ms01:profile:read"), requester.permissions());
        assertThrows(UnsupportedOperationException.class, () -> requester.roles().add("ADMIN"));
        assertThrows(UnsupportedOperationException.class, () -> requester.permissions().add("ms01:user:roles"));
    }

    /**
     * Los permisos son exactamente los que recibe la identidad, no se calculan a partir de los nombres de rol
     */
    @Test
    void permissions_areTheOnesReceived() {
        RequesterIdentity requester = TestIdentities.withPermissions(USER_ID, Set.of("ADMIN", "AUDITOR"),
                "ms01:user:read", "ms08:audit:read");

        assertEquals(Set.of("ms01:user:read", "ms08:audit:read"), requester.permissions());
        assertEquals(Set.of("ADMIN", "AUDITOR"), requester.roles());
    }

    /**
     * Una identidad armada con los permisos de varios roles sembrados trae la unión de ellos
     */
    @Test
    void permissions_ofSeveralSeededRoles_areTheUnion() {
        RequesterIdentity requester = TestIdentities.of(USER_ID, "ESTUDIANTE", "AUDITOR");

        Set<String> expected = new TreeSet<>(SeedRoles.PERMISSIONS.get("ESTUDIANTE"));
        expected.addAll(SeedRoles.PERMISSIONS.get("AUDITOR"));

        assertEquals(expected, requester.permissions());
    }

    /**
     * La verificación de un permiso se resuelve con los permisos recibidos y no con el nombre del rol
     */
    @Test
    void hasPermission_followsTheReceivedPermissions() {
        RequesterIdentity requester = TestIdentities.of(USER_ID, "ESTUDIANTE");
        RequesterIdentity adminName = TestIdentities.withPermissions(USER_ID, Set.of("ADMIN"), "ms01:profile:read");

        assertTrue(requester.hasPermission(Permission.MS01_PROFILE_READ));
        assertFalse(requester.hasPermission(Permission.MS01_USER_STATUS));
        assertFalse(adminName.hasPermission(Permission.MS01_USER_STATUS));
    }

    /**
     * La cabecera de roles va separada por comas y en orden alfabético
     */
    @Test
    void rolesHeader_isCommaSeparatedAndAlphabetical() {
        RequesterIdentity requester = TestIdentities.of(USER_ID, "PROFESOR", "ADMIN", "BRIGADISTA");

        assertEquals("ADMIN,BRIGADISTA,PROFESOR", requester.rolesHeader());
    }
}
