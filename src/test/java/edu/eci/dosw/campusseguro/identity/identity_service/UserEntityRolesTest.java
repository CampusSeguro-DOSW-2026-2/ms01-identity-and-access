package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * Los roles del usuario son un conjunto que se sincroniza en sitio (5_modelo_datos, cambio de roles en
 * lote): la coleccion que carga Hibernate no se reemplaza, asi solo se insertan los roles nuevos y se borran
 * los retirados de usuario_rol
 */
class UserEntityRolesTest {

    /**
     * Un usuario nuevo tiene un conjunto de roles vacio y nunca null
     */
    @Test
    void newUser_hasAnEmptyRoleSet() {
        UserEntity user = new UserEntity();

        assertNotNull(user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    /**
     * Asignar roles deja la misma coleccion con el contenido nuevo, no la reemplaza
     */
    @Test
    void setRoles_keepsTheSameCollectionInstance() {
        UserEntity user = new UserEntity();
        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE, UserRole.PROFESOR));
        Set<UserRole> loaded = user.getRoles();

        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE, UserRole.BRIGADISTA));

        assertSame(loaded, user.getRoles());
        assertEquals(Set.of(UserRole.ESTUDIANTE, UserRole.BRIGADISTA), user.getRoles());
    }

    /**
     * Se agregan los roles que faltaban y se retiran los que ya no estan
     */
    @Test
    void setRoles_addsTheNewRolesAndRemovesTheMissingOnes() {
        UserEntity user = new UserEntity();
        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE, UserRole.PROFESOR));

        user.setRoles(EnumSet.of(UserRole.PROFESOR, UserRole.ADMIN));

        assertEquals(Set.of(UserRole.PROFESOR, UserRole.ADMIN), user.getRoles());
    }

    /**
     * Asignar los mismos roles no cambia nada
     */
    @Test
    void setRoles_withTheSameRoles_changesNothing() {
        UserEntity user = new UserEntity();
        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE));

        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE));

        assertEquals(Set.of(UserRole.ESTUDIANTE), user.getRoles());
    }

    /**
     * Asignar null deja al usuario sin roles y conserva la coleccion
     */
    @Test
    void setRoles_null_leavesNoRolesAndKeepsTheCollection() {
        UserEntity user = new UserEntity();
        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE));
        Set<UserRole> loaded = user.getRoles();

        user.setRoles(null);

        assertSame(loaded, user.getRoles());
        assertTrue(user.getRoles().isEmpty());
    }

    /**
     * La entidad copia los roles recibidos, modificar el conjunto original despues no afecta al usuario
     */
    @Test
    void setRoles_copiesTheReceivedSet() {
        UserEntity user = new UserEntity();
        Set<UserRole> received = new HashSet<>();
        received.add(UserRole.ESTUDIANTE);
        user.setRoles(received);

        received.add(UserRole.ADMIN);

        assertEquals(Set.of(UserRole.ESTUDIANTE), user.getRoles());
    }

    /**
     * Asignar su propia coleccion no la vacia ni la modifica
     */
    @Test
    void setRoles_withItsOwnCollection_keepsTheRoles() {
        UserEntity user = new UserEntity();
        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE, UserRole.PROFESOR));

        user.setRoles(user.getRoles());

        assertEquals(Set.of(UserRole.ESTUDIANTE, UserRole.PROFESOR), user.getRoles());
    }

    /**
     * Asignar una vista de su propia coleccion tampoco pierde roles
     */
    @Test
    void setRoles_withAViewOfItsOwnCollection_keepsTheRoles() {
        UserEntity user = new UserEntity();
        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE, UserRole.PROFESOR));

        user.setRoles(Collections.unmodifiableSet(user.getRoles()));

        assertEquals(Set.of(UserRole.ESTUDIANTE, UserRole.PROFESOR), user.getRoles());
    }
}
