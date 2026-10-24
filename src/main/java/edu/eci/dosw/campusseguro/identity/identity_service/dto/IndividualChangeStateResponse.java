package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

public class IndividualChangeStateResponse {
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("estado_anterior")
    private UserState estadoAnterior;

    @JsonProperty("estado_nuevo")
    private UserState estadoNuevo;

    /**
     * 
     * @param entity
     * @param estadoPrevio
     * @return
     */
    public static IndividualChangeStateResponse fromEntity(UserEntity entity, UserState estadoPrevio) {
        IndividualChangeStateResponse dto = new IndividualChangeStateResponse();
        dto.setId(entity.getId());
        dto.setEstadoAnterior(estadoPrevio);
        dto.setEstadoNuevo(entity.getState());
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserState getEstadoAnterior() { return estadoAnterior; }
    public void setEstadoAnterior(UserState estadoAnterior) { this.estadoAnterior = estadoAnterior; }

    public UserState getEstadoNuevo() { return estadoNuevo; }
    public void setEstadoNuevo(UserState estadoNuevo) { this.estadoNuevo = estadoNuevo; }
}
