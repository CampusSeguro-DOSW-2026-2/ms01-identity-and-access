package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;

/**
 * The roles of the user are a set of entities that is synchronized in place (4_modelo-datos, batch role change): the
 * collection loaded by Hibernate is not replaced, so only the new roles are inserted and the removed ones are deleted
 * from user_roles
 */
class UserEntityRolesTest {

    private final RoleEntity student = TestEntities.role("ESTUDIANTE", "ms01:profile:read");
    private final RoleEntity professor = TestEntities.role("PROFESOR", "ms01:profile:read");
    private final RoleEntity brigadier = TestEntities.role("BRIGADISTA", "ms01:profile:read");
    private final RoleEntity admin = TestEntities.role("ADMIN", "ms01:profile:read");

        /**
     * A new user has an empty set of roles and never null
     */
    @Test
    void newUser_hasAnEmptyRoleSet() {
        UserEntity user = new UserEntity();

        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

        /**
     * Assigning roles leaves the same collection with the new content, it does not replace it
     */
    @Test
    void setRoles_keepsTheSameCollectionInstance() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student, professor));
        Set<RoleEntity> loaded = user.getRoles();

        user.setRoles(Set.of(student, brigadier));

        assertSame(loaded, user.getRoles());
        assertEquals(Set.of(student, brigadier), user.getRoles());
    }

        /**
     * The roles that were missing are added and those that are no longer there are removed
     */
    @Test
    void setRoles_addsTheNewRolesAndRemovesTheMissingOnes() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student, professor));

        user.setRoles(Set.of(professor, admin));

        assertEquals(Set.of(professor, admin), user.getRoles());
    }

        /**
     * Assigning the same roles changes nothing
     */
    @Test
    void setRoles_withTheSameRoles_changesNothing() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student));

        user.setRoles(Set.of(student));

        assertEquals(Set.of(student), user.getRoles());
    }

        /**
     * Assigning null leaves the user without roles and keeps the collection
     */
    @Test
    void setRoles_null_leavesNoRolesAndKeepsTheCollection() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student));
        Set<RoleEntity> loaded = user.getRoles();

        user.setRoles(null);

        assertSame(loaded, user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

        /**
     * The entity copies the received roles, modifying the original set afterwards does not affect the user
     */
    @Test
    void setRoles_copiesTheReceivedSet() {
        UserEntity user = new UserEntity();
        Set<RoleEntity> received = new HashSet<>();
        received.add(student);
        user.setRoles(received);

        received.add(admin);

        assertEquals(Set.of(student), user.getRoles());
    }

        /**
     * Assigning its own collection does not empty or modify it
     */
    @Test
    void setRoles_withItsOwnCollection_keepsTheRoles() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student, professor));

        user.setRoles(user.getRoles());

        assertEquals(Set.of(student, professor), user.getRoles());
    }

        /**
     * Assigning a view of its own collection does not lose roles either
     */
    @Test
    void setRoles_withAViewOfItsOwnCollection_keepsTheRoles() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student, professor));

        user.setRoles(Collections.unmodifiableSet(user.getRoles()));

        assertEquals(Set.of(student, professor), user.getRoles());
    }

        /**
     * The names of the roles come out sorted alphabetically
     */
    @Test
    void getRoleNames_returnsTheNamesSorted() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(professor, admin, student));

        assertEquals(List.of("ADMIN", "ESTUDIANTE", "PROFESOR"), user.getRoleNames());
    }

        /**
     * A user without roles has no role names
     */
    @Test
    void getRoleNames_withoutRoles_isEmpty() {
        assertTrue(new UserEntity().getRoleNames().isEmpty());
    }
}
