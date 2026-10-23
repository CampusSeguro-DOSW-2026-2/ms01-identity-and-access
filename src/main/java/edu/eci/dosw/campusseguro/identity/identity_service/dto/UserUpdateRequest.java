package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

public class UserUpdateRequest {
    
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
    @Null(message = "El correo no se puede modificar desde el perfil")
    private String correo;

    @JsonProperty("roles")
    @Null(message = "Los roles no se pueden modificar desde el perfil")
    private Object roles;

    @JsonProperty("estado")
    @Null(message = "Los roles no se pueden modificar desde el perfil")
    private UserState estado;


    public String getFirstName(){ return this.primer_nombre; }
    public void setFirstName(String name){ this.primer_nombre = name; }

    public String getSecondName(){ return this.segundo_nombre; }
    public void setSecondName(String name){ this.segundo_nombre = name; }

    public String getFirstLastName(){ return this.primer_apellido; }
    public void setFirstLastName(String lastName){ this.primer_apellido = lastName; }

    public String getSecondLastName(){ return this.segundo_apellido; }
    public void setSecondLastName(String lastName){ this.segundo_apellido = lastName; }
}
