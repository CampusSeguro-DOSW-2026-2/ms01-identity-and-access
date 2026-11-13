package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;

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
        user.setRoles(Set.of("ESTUDIANTE", "PROFESOR"));
        Set<String> loaded = user.getRoles();

        user.setRoles(Set.of("ESTUDIANTE", "BRIGADISTA"));

        assertSame(loaded, user.getRoles());
        assertEquals(Set.of("ESTUDIANTE", "BRIGADISTA"), user.getRoles());
    }

    /**
     * Se agregan los roles que faltaban y se retiran los que ya no estan
     */
    @Test
    void setRoles_addsTheNewRolesAndRemovesTheMissingOnes() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of("ESTUDIANTE", "PROFESOR"));

        user.setRoles(Set.of("PROFESOR", "ADMIN"));

        assertEquals(Set.of("PROFESOR", "ADMIN"), user.getRoles());
    }

    /**
     * Asignar los mismos roles no cambia nada
     */
    @Test
    void setRoles_withTheSameRoles_changesNothing() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of("ESTUDIANTE"));

        user.setRoles(Set.of("ESTUDIANTE"));

        assertEquals(Set.of("ESTUDIANTE"), user.getRoles());
    }

    /**
     * Asignar null deja al usuario sin roles y conserva la coleccion
     */
    @Test
    void setRoles_null_leavesNoRolesAndKeepsTheCollection() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of("ESTUDIANTE"));
        Set<String> loaded = user.getRoles();

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
        Set<String> received = new HashSet<>();
        received.add("ESTUDIANTE");
        user.setRoles(received);

        received.add("ADMIN");

        assertEquals(Set.of("ESTUDIANTE"), user.getRoles());
    }

    /**
     * Asignar su propia coleccion no la vacia ni la modifica
     */
    @Test
    void setRoles_withItsOwnCollection_keepsTheRoles() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of("ESTUDIANTE", "PROFESOR"));

        user.setRoles(user.getRoles());

        assertEquals(Set.of("ESTUDIANTE", "PROFESOR"), user.getRoles());
    }

    /**
     * Asignar una vista de su propia coleccion tampoco pierde roles
     */
    @Test
    void setRoles_withAViewOfItsOwnCollection_keepsTheRoles() {
        UserEntity user = new UserEntity();
        user.setRoles(Set.of("ESTUDIANTE", "PROFESOR"));

        user.setRoles(Collections.unmodifiableSet(user.getRoles()));

        assertEquals(Set.of("ESTUDIANTE", "PROFESOR"), user.getRoles());
    }
}
