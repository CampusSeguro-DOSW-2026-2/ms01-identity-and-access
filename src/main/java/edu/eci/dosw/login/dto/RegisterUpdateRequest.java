package edu.eci.dosw.login.dto;

import edu.eci.dosw.login.entity.UserRol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterUpdateRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 10, message = "La contraseña no puede superar los 10 caracteres")
    private String password;

    @NotNull(message = "El rol es obligatorio")
    private UserRol rol;

    /**
     * Los gets y sets necesarios para esta seccion de actualizacion de la informacion en la etapa de registro
     * de un usuario
     */

    public String getEmail(){ return this.email; }
    public void setEmail(String email){ this.email = email; }

    public String getPassword(){ return this.password; }
    public void setPassword(String password){ this.password = password; }

    public UserRol getRol(){ return this.rol; }
    public void setRol(UserRol rol){ this.rol = rol; }
}
