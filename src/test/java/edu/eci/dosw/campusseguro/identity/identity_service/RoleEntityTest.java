package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;

/**
 * Entidad de la tabla roles y corte de sesión de la entidad de usuario
 */
class RoleEntityTest {

    /**
     * Un rol nuevo no es de sistema, no tiene permisos, ni corte, ni fechas
     */
    @Test
    void newRole_hasSafeDefaults() {
        RoleEntity role = new RoleEntity();

        assertFalse(role.isSystem());
        assertTrue(role.getPermissions().isEmpty());
        assertNull(role.getValidFrom());
        assertNull(role.getCreatedAt());
        assertNull(role.getUpdatedAt());
    }

    /**
     * Los datos del rol se guardan y se devuelven
     */
    @Test
    void setters_storeTheValues() {
        RoleEntity role = new RoleEntity();
        Instant cut = Instant.parse("2026-11-11T12:00:00Z");

        role.setName("MONITOR");
        role.setDescription("Apoya la evacuación");
        role.setPermissions(List.of("ms04:checkin:write"));
        role.setSystem(true);
        role.setValidFrom(cut);

        assertEquals("MONITOR", role.getName());
        assertEquals("Apoya la evacuación", role.getDescription());
        assertEquals(List.of("ms04:checkin:write"), role.getPermissions());
        assertTrue(role.isSystem());
        assertEquals(cut, role.getValidFrom());
    }

    /**
     * Cambiar la lista que se entregó no cambia los permisos guardados
     */
    @Test
    void setPermissions_copiesTheList() {
        List<String> original = new ArrayList<>(List.of("ms04:checkin:write"));
        RoleEntity role = new RoleEntity();

        role.setPermissions(original);
        original.add("ms01:profile:read");

        assertNotSame(original, role.getPermissions());
        assertEquals(List.of("ms04:checkin:write"), role.getPermissions());
    }

    /**
     * El corte de sesión de un rol se puede quitar
     */
    @Test
    void validFrom_canBeCleared() {
        RoleEntity role = new RoleEntity();
        role.setValidFrom(Instant.parse("2026-11-11T12:00:00Z"));

        role.setValidFrom(null);

        assertNull(role.getValidFrom());
    }

    /**
     * Un usuario nuevo no tiene corte de sesión y se puede escribir y quitar
     */
    @Test
    void userValidFrom_defaultsToNullAndCanBeSet() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        Instant cut = Instant.parse("2026-11-11T12:00:00Z");

        assertNull(user.getValidFrom());
        user.setValidFrom(cut);
        assertEquals(cut, user.getValidFrom());
        user.setValidFrom(null);
        assertNull(user.getValidFrom());
    }
}
