package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * Data of a user (User schema of the contract), used in the profile query and update, in the query by id and in the
 * elements of the list, the second name and the second lastname are omitted when they are empty
 *
 * @param id id of the user
 * @param firstName first name
 * @param secondName second name, omitted if it has none
 * @param firstLastName first lastname
 * @param secondLastName second lastname, omitted if it has none
 * @param email institutional email
 * @param roles roles of the user, in alphabetical order
 * @param state status of the account
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileResponse(
        UUID id,
        @JsonProperty("primer_nombre") String firstName,
        @JsonProperty("segundo_nombre") String secondName,
        @JsonProperty("primer_apellido") String firstLastName,
        @JsonProperty("segundo_apellido") String secondLastName,
        @JsonProperty("correo") String email,
        List<String> roles,
        @JsonProperty("estado") UserState state) {

        /**
     * Builds the response from the saved user
     *
     * @param entity saved user
     * @return the response with all the data of the user and its sorted roles
     */
    public static UserProfileResponse fromEntity(UserEntity entity) {
        return new UserProfileResponse(
                entity.getId(),
                entity.getFirstName(),
                entity.getSecondName(),
                entity.getFirstLastName(),
                entity.getSecondLastName(),
                entity.getEmail(),
                entity.getRoleNames(),
                entity.getState());
    }
}
