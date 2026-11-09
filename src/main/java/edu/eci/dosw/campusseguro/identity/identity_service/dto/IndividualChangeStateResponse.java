package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * Resultado del cambio de estado de un usuario (elemento de resultados de ChangeStatusResponse)
 *
 * @param id id del usuario
 * @param estadoAnterior estado que tenía antes del cambio
 * @param estadoNuevo estado que tiene después del cambio
 */
public record IndividualChangeStateResponse(
        UUID id,
        @JsonProperty("estado_anterior") UserState estadoAnterior,
        @JsonProperty("estado_nuevo") UserState estadoNuevo) {

    /**
     * Construye el resultado de un usuario
     *
     * @param entity usuario con el estado ya cambiado
     * @param estadoPrevio estado que tenía antes del cambio
     * @return el resultado con el estado anterior y el nuevo
     */
    public static IndividualChangeStateResponse fromEntity(UserEntity entity, UserState estadoPrevio) {
        return new IndividualChangeStateResponse(entity.getId(), estadoPrevio, entity.getState());
    }
}
