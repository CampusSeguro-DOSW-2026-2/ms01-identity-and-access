package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

public class UserProfileResponse {

    @JsonProperty("primer_nombre")
    private String firstName;

    @JsonProperty("segundo_nombre")
    private String secondName;

    @JsonProperty("primer_apellido")
    private String firstLastName;

    @JsonProperty("segundo_apellido")
    private String secondLastName;

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
    public static UserProfileResponse fromEntity(UserEntity entity){
        UserProfileResponse response = new UserProfileResponse();
        response.setFirstName(entity.getFirstName());
        response.setSecondName(entity.getSecondName());
        response.setFirstLastName(entity.getFirstLastName());
        response.setSecondLastName(entity.getSecondLastName());
        response.setEmail(entity.getEmail());
        response.setRoles(UserRole.sortedByName(entity.getRoles()));
        response.setState(entity.getState());
        return response;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getSecondName() { return secondName; }
    public void setSecondName(String secondName) { this.secondName = secondName; }

    public String getFirstLastName() { return firstLastName; }
    public void setFirstLastName(String firstLastName) { this.firstLastName = firstLastName; }

    public String getSecondLastName() { return secondLastName; }
    public void setSecondLastName(String secondLastName) { this.secondLastName = secondLastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public List<UserRole> getRoles() { return roles; }
    public void setRoles(List<UserRole> roles) { this.roles = roles; }

    public UserState getState() { return state; }
    public void setState(UserState state) { this.state = state; }
}
