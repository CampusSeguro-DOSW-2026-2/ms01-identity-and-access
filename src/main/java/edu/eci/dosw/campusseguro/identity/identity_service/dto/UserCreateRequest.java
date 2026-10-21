package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserCreateRequest {
    
    @NotBlank(message = "Tiene que indicar su primer nombre")
    @Size(max = 30, message = "El nombre no debe superar los 30 caracteres")
    private String primer_nombre;

    @Size(max = 30, message = "El nombre no debe superar los 30 caracteres")
    private String segundo_nombre;

    @NotBlank(message = "Tiene que indicar su primer nombre")
    @Size(max = 30, message = "El nombre no debe superar los 30 caracteres")
    private String primer_apellido;

    @Size(max = 30, message = "El nombre no debe superar los 30 caracteres")
    private String segundo_apellido;

    @NotBlank(message = "Tiene que indicar su correo electronico")
    @Size(max = 100, message = "El correo debe tener un tamaño menor a 100 caracteres")
    private String correo;

    @NotBlank(message = "Tiene que indicar la contraseña")
    @Size(max = 100, message = "El tamaño de la contraseña no puede superar los 100 caracteres")
    private String password_hash;

    @NotNull(message = "El rol del usuario es obligatorio")
    private List<UserRole> rol;

    @NotNull(message = "El estado del usuario es obligatorio")
    private UserState estado;


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
