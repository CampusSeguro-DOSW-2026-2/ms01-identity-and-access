package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * Response of POST /api/v1/auth/register (RegisterResponse schema of the contract), it never includes the password or
 * its hash (RF-MS01-01)
 *
 * @param id id of the created user
 * @param email institutional email
 * @param roles roles of the user, in alphabetical order
 * @param state status of the account
 */
public record UserResponse(
        UUID id,
        @JsonProperty("correo") String email,
        List<String> roles,
        @JsonProperty("estado") UserState state) {

        /**
     * Builds the response from the saved user
     *
     * @param entity saved user
     * @return the response with the id, the email, the sorted roles and the status
     */
    public static UserResponse fromEntity(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getRoleNames(),
                entity.getState());
    }
}
