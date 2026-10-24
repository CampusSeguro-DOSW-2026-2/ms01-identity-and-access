package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class ChangeStatusRequest {
    
    @JsonProperty("ids")
    @NotEmpty(message = "La lista de IDs no puede estar vacia para realizar esta accion")
    private List<UUID> listIds;

    @JsonProperty("estado")
    @NotNull(message = "El estado es obligatorio")
    private UserState state;

    public List<UUID> getListIds(){ return this.listIds; }
    public void setListIds(List<UUID> list){ this.listIds = list; }

    public UserState getState(){ return this.state; }
    public void setState(UserState estado){ this.state = estado; }
}
