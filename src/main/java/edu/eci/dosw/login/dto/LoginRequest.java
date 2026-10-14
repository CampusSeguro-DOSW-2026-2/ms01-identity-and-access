package edu.eci.dosw.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {
    /**
     * Valores esperados de entrada, cuando un usuario quiere logearse en la aplicacion web, de tal
     * forma que los datos consultados ya estan en la base de datos
     */

    @NotBlank(message = "El correo es obligatorio")
    @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 10, message = "La contraseña no puede superar los 10 caracteres")
    private String password;

    /**
     * Los gets y sets necesarios para esta seccion de consulta de la informacion en la etapa de login
     * de un usuario
     */

    public String getEmail(){ return this.email; }
    public void setEmail(String email){ this.email = email; }

    public String getPassword(){ return this.password; }
    public void setPassword(String password){ this.password = password; }
}
