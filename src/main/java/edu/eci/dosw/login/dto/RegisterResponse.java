package edu.eci.dosw.login.dto;

import edu.eci.dosw.login.entity.UserEntity;
import edu.eci.dosw.login.entity.UserRol;

public class RegisterResponse {
    private Long id;
    private String email;
    private UserRol rol;

    /**
     * 
     * @param entity
     * @return
     */
    public static RegisterResponse fromEntity(UserEntity entity){
        RegisterResponse response = new RegisterResponse();
        response.setId(entity.getId());
        response.setEmail(entity.getEmail());
        response.setRol(entity.getRol());
        return response;
    }

    public Long getId(){ return this.id; }
    public void setId(Long id){ this.id = id; }

    public String getEmail(){ return this.email; }
    public void setEmail(String email){ this.email = email; }

    public UserRol getRol(){ return this.rol; }
    public void setRol(UserRol rol){ this.rol = rol; }
}
