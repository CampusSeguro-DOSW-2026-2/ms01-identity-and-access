package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;

/**
 * Entity of the roles table and session cut of the user entity
 */
class RoleEntityTest {

        /**
     * A new role is not a system role, has no permissions, no cut and no dates
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
     * The data of the role is stored and returned
     */
    @Test
    void setters_storeTheValues() {
        RoleEntity role = new RoleEntity();
        Instant cut = Instant.parse("2026-11-11T12:00:00Z");

        role.setName("MONITOR");
        role.setDescription("Apoya la evacuación");
        PermissionEntity checkin = TestEntities.permission("ms04:checkin:write");
        role.setPermissions(Set.of(checkin));
        role.setSystem(true);
        role.setValidFrom(cut);

        assertEquals("MONITOR", role.getName());
        assertEquals("Apoya la evacuación", role.getDescription());
        assertEquals(Set.of(checkin), role.getPermissions());
        assertTrue(role.isSystem());
        assertEquals(cut, role.getValidFrom());
    }

        /**
     * Changing the set that was delivered does not change the stored permissions
     */
    @Test
    void setPermissions_copiesTheSet() {
        PermissionEntity checkin = TestEntities.permission("ms04:checkin:write");
        Set<PermissionEntity> original = new HashSet<>(Set.of(checkin));
        RoleEntity role = new RoleEntity();

        role.setPermissions(original);
        original.add(TestEntities.permission("ms01:profile:read"));

        assertNotSame(original, role.getPermissions());
        assertEquals(Set.of(checkin), role.getPermissions());
    }

        /**
     * Assigning permissions keeps the same collection and only changes what is necessary
     */
    @Test
    void setPermissions_keepsTheSameCollectionInstance() {
        PermissionEntity checkin = TestEntities.permission("ms04:checkin:write");
        PermissionEntity read = TestEntities.permission("ms01:profile:read");
        PermissionEntity write = TestEntities.permission("ms01:profile:write");
        RoleEntity role = new RoleEntity();
        role.setPermissions(Set.of(checkin, read));
        Set<PermissionEntity> loaded = role.getPermissions();

        role.setPermissions(Set.of(read, write));

        assertEquals(Set.of(read, write), loaded);
        assertEquals(Set.of(read, write), role.getPermissions());
    }

        /**
     * Assigning null leaves the role without permissions
     */
    @Test
    void setPermissions_null_leavesNoPermissions() {
        RoleEntity role = TestEntities.role("MONITOR", "ms04:checkin:write");

        role.setPermissions(null);

        assertTrue(role.getPermissions().isEmpty());
    }

        /**
     * Assigning its own collection does not empty it
     */
    @Test
    void setPermissions_withItsOwnCollection_keepsThePermissions() {
        RoleEntity role = TestEntities.role("MONITOR", "ms04:checkin:write", "ms01:profile:read");

        role.setPermissions(role.getPermissions());

        assertEquals(2, role.getPermissions().size());
    }

        /**
     * A permission entity stores its code and its description
     */
    @Test
    void permissionEntity_storesNameAndDescription() {
        PermissionEntity permission = new PermissionEntity();

        permission.setName("ms04:checkin:write");
        permission.setDescription("Registrar el check-in");

        assertNull(permission.getId());
        assertEquals("ms04:checkin:write", permission.getName());
        assertEquals("Registrar el check-in", permission.getDescription());
    }

        /**
     * The session cut of a role can be removed
     */
    @Test
    void validFrom_canBeCleared() {
        RoleEntity role = new RoleEntity();
        role.setValidFrom(Instant.parse("2026-11-11T12:00:00Z"));

        role.setValidFrom(null);

        assertNull(role.getValidFrom());
    }

        /**
     * A new user has no session cut and one can be written and removed
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

    /**
     * The collection returned by getPermissions is read-only: it only changes through its setter
     */
    @Test
    void getPermissions_isReadOnly() {
        RoleEntity role = new RoleEntity();
        role.setPermissions(Set.of(TestEntities.permission("ms01:profile:read")));

        assertThrows(UnsupportedOperationException.class, () -> role.getPermissions().clear());
    }
}
