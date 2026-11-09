package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * Los roles de un usuario se guardan en un conjunto sin orden y las respuestas los entregan en orden
 * alfabetico por nombre (5_modelo_datos)
 */
class UserRoleSortedByNameTest {

    /**
     * Todos los roles quedan en orden alfabetico, que no es el orden de declaracion del enum
     */
    @Test
    void sortedByName_allRoles_areInAlphabeticalOrder() {
        List<UserRole> sorted = UserRole.sortedByName(EnumSet.allOf(UserRole.class));

        assertEquals(List.of(
                UserRole.ADMIN,
                UserRole.ADMINISTRATIVO,
                UserRole.AUDITOR,
                UserRole.BRIGADISTA,
                UserRole.COORDINADOR,
                UserRole.ESTUDIANTE,
                UserRole.ORGANIZADOR,
                UserRole.PROFESOR), sorted);
    }

    /**
     * El resultado no depende del orden de iteracion del conjunto recibido
     */
    @Test
    void sortedByName_hashSet_isStillAlphabetical() {
        Set<UserRole> received = new HashSet<>(List.of(UserRole.PROFESOR, UserRole.ADMIN, UserRole.ESTUDIANTE));

        assertEquals(List.of(UserRole.ADMIN, UserRole.ESTUDIANTE, UserRole.PROFESOR),
                UserRole.sortedByName(received));
    }

    /**
     * Null equivale a ninguno
     */
    @Test
    void sortedByName_null_returnsAnEmptyList() {
        assertTrue(UserRole.sortedByName(null).isEmpty());
    }

    /**
     * Un conjunto vacio da una lista vacia
     */
    @Test
    void sortedByName_empty_returnsAnEmptyList() {
        assertTrue(UserRole.sortedByName(Set.of()).isEmpty());
    }

    /**
     * No modifica el conjunto recibido
     */
    @Test
    void sortedByName_doesNotModifyTheReceivedSet() {
        Set<UserRole> received = EnumSet.of(UserRole.PROFESOR, UserRole.ADMIN);

        UserRole.sortedByName(received);

        assertEquals(EnumSet.of(UserRole.PROFESOR, UserRole.ADMIN), received);
    }

    /**
     * La lista devuelta es de solo lectura
     */
    @Test
    void sortedByName_resultIsReadOnly() {
        List<UserRole> sorted = UserRole.sortedByName(EnumSet.of(UserRole.ADMIN));

        assertThrows(UnsupportedOperationException.class, () -> sorted.add(UserRole.PROFESOR));
    }
}
