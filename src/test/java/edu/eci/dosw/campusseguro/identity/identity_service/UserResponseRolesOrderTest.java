package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * Los roles de las respuestas de usuario salen en orden alfabetico por nombre (5_modelo_datos) aunque la
 * entidad los guarde en un conjunto sin orden
 */
class UserResponseRolesOrderTest {

    private UserEntity entity;

    /**
     * Prepara un usuario con tres roles cuyo orden alfabetico no coincide con el de declaracion del enum
     */
    @BeforeEach
    void setUp() {
        entity = new UserEntity();
        entity.setFirstName("Carlos");
        entity.setFirstLastName("Gomez");
        entity.setEmail("carlos.gomez@mail.escuelaing.edu.co");
        entity.setState(UserState.ACTIVO);
        entity.setRoles(EnumSet.of(UserRole.PROFESOR, UserRole.ESTUDIANTE, UserRole.ADMIN));
    }

    /**
     * UserResponse entrega los roles ordenados
     */
    @Test
    void userResponse_fromEntity_sortsTheRoles() {
        UserResponse response = UserResponse.fromEntity(entity);

        assertEquals(List.of(UserRole.ADMIN, UserRole.ESTUDIANTE, UserRole.PROFESOR), response.getRoles());
    }

    /**
     * UserProfileResponse entrega los roles ordenados
     */
    @Test
    void userProfileResponse_fromEntity_sortsTheRoles() {
        UserProfileResponse response = UserProfileResponse.fromEntity(entity);

        assertEquals(List.of(UserRole.ADMIN, UserRole.ESTUDIANTE, UserRole.PROFESOR), response.getRoles());
    }

    /**
     * Un usuario sin roles da una lista vacia y no null
     */
    @Test
    void fromEntity_userWithoutRoles_returnsAnEmptyList() {
        entity.setRoles(null);

        assertTrue(UserResponse.fromEntity(entity).getRoles().isEmpty());
        assertTrue(UserProfileResponse.fromEntity(entity).getRoles().isEmpty());
    }
}
