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
 * Los roles del usuario son un conjunto de entidades que se sincroniza en sitio (4_modelo-datos, cambio de roles
 * en lote): la colección que carga Hibernate no se reemplaza, así solo se insertan los roles nuevos y se borran
 * los retirados de usuario_rol
 */
class UserEntityRolesTest {

    private final RoleEntity student = TestEntities.role("ESTUDIANTE", "ms01:profile:read");
    private final RoleEntity professor = TestEntities.role("PROFESOR", "ms01:profile:read");
    private final RoleEntity brigadier = TestEntities.role("BRIGADISTA", "ms01:profile:read");
    private final RoleEntity admin = TestEntities.role("ADMIN", "ms01:profile:read");

    /**
     * Un usuario nuevo tiene un conjunto de roles vacío y nunca null
     */
    @Test
    void newUser_hasAnEmptyRoleSet() {
        UserEntity user = new UserEntity();

        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    /**
     * Asignar roles deja la misma colección con el contenido nuevo, no la reemplaza
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
     * Se agregan los roles que faltaban y se retiran los que ya no están
     */
    @Test
    void setRoles_addsTheNewRolesAndRemovesTheMissingOnes() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student, professor));

        user.setRoles(Set.of(professor, admin));

        assertEquals(Set.of(professor, admin), user.getRoles());
    }

    /**
     * Asignar los mismos roles no cambia nada
     */
    @Test
    void setRoles_withTheSameRoles_changesNothing() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student));

        user.setRoles(Set.of(student));

        assertEquals(Set.of(student), user.getRoles());
    }

    /**
     * Asignar null deja al usuario sin roles y conserva la colección
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
     * La entidad copia los roles recibidos, modificar el conjunto original después no afecta al usuario
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
     * Asignar su propia colección no la vacía ni la modifica
     */
    @Test
    void setRoles_withItsOwnCollection_keepsTheRoles() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student, professor));

        user.setRoles(user.getRoles());

        assertEquals(Set.of(student, professor), user.getRoles());
    }

    /**
     * Asignar una vista de su propia colección tampoco pierde roles
     */
    @Test
    void setRoles_withAViewOfItsOwnCollection_keepsTheRoles() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(student, professor));

        user.setRoles(Collections.unmodifiableSet(user.getRoles()));

        assertEquals(Set.of(student, professor), user.getRoles());
    }

    /**
     * Los nombres de los roles salen ordenados alfabéticamente
     */
    @Test
    void getRoleNames_returnsTheNamesSorted() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of(professor, admin, student));

        assertEquals(List.of("ADMIN", "ESTUDIANTE", "PROFESOR"), user.getRoleNames());
    }

    /**
     * Un usuario sin roles no tiene nombres de rol
     */
    @Test
    void getRoleNames_withoutRoles_isEmpty() {
        assertTrue(new UserEntity().getRoleNames().isEmpty());
    }
}
