package edu.eci.dosw.campusseguro.identity.identity_service.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserCreateRequest {
    
    @JsonProperty("primer_nombre")
    @NotBlank(message = "Tiene que indicar su primer nombre")
    @Size(min = 2, max = 30, message = "El primer nombre debe superar los 2 carateres pero no superar los 30 caracteres")
    private String primer_nombre;

    @JsonProperty("segundo_nombre")
    @Size(min = 2, max = 30, message = "El segundo nombre debe superar los 2 carateres pero no superar los 30 caracteres")
    private String segundo_nombre;

    @JsonProperty("primer_apellido")
    @NotBlank(message = "Tiene que indicar su primer nombre")
    @Size(min = 2, max = 30, message = "El primer apellido debe superar los 2 carateres pero no superar los 30 caracteres")
    private String primer_apellido;

    @JsonProperty("segundo_apellido")
    @Size(min = 2, max = 30, message = "El segundo apellido debe superar los 2 carateres pero no superar los 30 caracteres")
    private String segundo_apellido;

    @JsonProperty("correo")
    @NotBlank(message = "Tiene que indicar su correo electronico")
    @Pattern(regexp = "(?i)^[A-Za-z0-9._%+-]+@(mail\\.)?escuelaing\\.edu\\.co$", message = "Debe usar su correo institucional")
    @Size(max = 100, message = "El correo debe tener un tamaño menor a 100 caracteres")
    private String correo;

    @JsonProperty("password")
    @NotBlank(message = "Tiene que indicar la contraseña")
    @Size(min = 8, max = 72, message = "El tamaño de la contraseña tiene que ser entre 8 a 100 caracteres")
    private String password_hash;


    public String getFirstName(){ return this.primer_nombre; }
    public void setFirstName(String name){ this.primer_nombre = name; }

    public String getSecondName(){ return this.segundo_nombre; }
    public void setSecondName(String name){ this.segundo_nombre = name; }

    public String getFirstLastName(){ return this.primer_apellido; }
    public void setFirstLastName(String lastName){ this.primer_apellido = lastName; }

    public String getSecondLastName(){ return this.segundo_apellido; }
    public void setSecondLastName(String lastName){ this.segundo_apellido = lastName; }

    public String getEmail(){ return this.correo; }
    public void setEmail(String email){ this.correo = email; }

    public String getPassword(){ return this.password_hash; }
    public void setPassword(String password){ this.password_hash = password; }
}
