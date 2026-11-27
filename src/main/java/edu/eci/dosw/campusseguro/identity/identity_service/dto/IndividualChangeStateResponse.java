package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * Result of the status change of a user (element of the results of ChangeStatusResponse)
 *
 * @param id id of the user
 * @param estadoAnterior status it had before the change
 * @param estadoNuevo status it has after the change
 */
public record IndividualChangeStateResponse(
        UUID id,
        @JsonProperty("estado_anterior") UserState estadoAnterior,
        @JsonProperty("estado_nuevo") UserState estadoNuevo) {

        /**
     * Builds the result of a user
     *
     * @param entity user with the status already changed
     * @param estadoPrevio status it had before the change
     * @return the result with the previous and the new status
     */
    public static IndividualChangeStateResponse fromEntity(UserEntity entity, UserState estadoPrevio) {
        return new IndividualChangeStateResponse(entity.getId(), estadoPrevio, entity.getState());
    }
}
