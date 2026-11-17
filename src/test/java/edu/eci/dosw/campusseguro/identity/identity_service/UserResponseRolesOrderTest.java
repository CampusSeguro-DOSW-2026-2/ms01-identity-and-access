package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * The roles of the user responses come out in alphabetical order by name (5_modelo_datos) even if the entity stores
 * them in an unordered set
 */
class UserResponseRolesOrderTest {

    private UserEntity entity;

        /**
     * Prepares a user with three roles whose alphabetical order does not match the declaration order of the enum
     */
    @BeforeEach
    void setUp() {
        entity = new UserEntity();
        entity.setFirstName("Carlos");
        entity.setFirstLastName("Gomez");
        entity.setEmail("carlos.gomez@escuelaing.edu.co");
        entity.setState(UserState.ACTIVO);
        entity.setRoles(TestEntities.roles("PROFESOR", "ESTUDIANTE", "ADMIN"));
    }

        /**
     * UserResponse delivers the roles sorted
     */
    @Test
    void userResponse_fromEntity_sortsTheRoles() {
        UserResponse response = UserResponse.fromEntity(entity);

        assertEquals(List.of("ADMIN", "ESTUDIANTE", "PROFESOR"), response.roles());
    }

        /**
     * UserProfileResponse delivers the roles sorted
     */
    @Test
    void userProfileResponse_fromEntity_sortsTheRoles() {
        UserProfileResponse response = UserProfileResponse.fromEntity(entity);

        assertEquals(List.of("ADMIN", "ESTUDIANTE", "PROFESOR"), response.roles());
    }

        /**
     * A user without roles gives an empty list and not null
     */
    @Test
    void fromEntity_userWithoutRoles_returnsAnEmptyList() {
        entity.setRoles(null);

        assertTrue(UserResponse.fromEntity(entity).roles().isEmpty());
        assertTrue(UserProfileResponse.fromEntity(entity).roles().isEmpty());
    }
}
