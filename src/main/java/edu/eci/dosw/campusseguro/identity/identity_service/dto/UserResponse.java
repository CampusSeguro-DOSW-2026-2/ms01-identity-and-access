package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

public class UserResponse {
    private UUID id;
 
    @JsonProperty("correo")
    private String email;
 
    @JsonProperty("roles")
    private List<UserRole> roles;
 
    @JsonProperty("estado")
    private UserState state;

    /**
     * 
     * @param entity
     * @return
     */
    public static UserResponse fromEntity(UserEntity entity){
        UserResponse response = new UserResponse();
        response.setId(entity.getId());
        response.setEmail(entity.getEmail());
        response.setRoles(UserRole.sortedByName(entity.getRoles()));
        response.setState(entity.getState());
        return response;
    }
        
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
 
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
 
    public List<UserRole> getRoles() { return roles; }
    public void setRoles(List<UserRole> roles) { this.roles = roles; }
 
    public UserState getState() { return state; }
    public void setState(UserState state) { this.state = state; }
}
