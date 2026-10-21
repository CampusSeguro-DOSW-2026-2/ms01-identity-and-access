package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

public class UserResponse {
    private UUID id;
    private String primer_nombre;
    private String segundo_nombre;
    private String primer_apellido;
    private String segundo_apellido;
    private String correo;
    private String password_hash;
    private List<UserRole> rol;
    private UserState estado;

    /**
     * 
     * @param entity
     * @return
     */
    public static UserResponse fromEntity(UserEntity entity){
        UserResponse response = new UserResponse();
        response.setId(entity.getId());
        response.setFirstName(entity.getFirstName());
        response.setSecondtName(entity.getSecondName());
        response.setFirstLastName(entity.getFirstLastName());
        response.setSecondLastName(entity.getSecondLastName());
        response.setEmail(entity.getEmail());
        response.setPassword(entity.getPassword());
        response.setRole(entity.getRole());
        response.setState(entity.getState());
        return response;
    }
        
    public UUID getId(){ return this.id; }
    public void setId(UUID id){ this.id = id; }

    public String getFirstName(){ return this.primer_nombre; }
    public void setFirstName(String name){ this.primer_nombre = name; }

    public String getSecondName(){ return this.segundo_nombre; }
    public void setSecondtName(String name){ this.segundo_nombre = name; }

    public String getFirstLastName(){ return this.primer_apellido; }
    public void setFirstLastName(String lastName){ this.primer_apellido = lastName; }

    public String getSecondLastName(){ return this.segundo_apellido; }
    public void setSecondLastName(String lastName){ this.segundo_apellido = lastName; }

    public String getEmail(){ return this.correo; }
    public void setEmail(String email){ this.correo = email; }

    public String getPassword(){ return this.password_hash; }
    public void setPassword(String password){ this.password_hash = password; }

    public List<UserRole> getRole(){ return this.rol; }
    public void setRole(List<UserRole> role){ this.rol = role; }

    public UserState getState(){ return this.estado; }
    public void setState(UserState state){ this.estado = state; }
}
