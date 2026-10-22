package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/v1/auth/login
 */
public class LoginRequest {

    public static final String EMAIL_MESSAGE = "Ingrese un correo válido.";
    public static final String PASSWORD_MESSAGE = "Ingrese su contraseña.";
    public static final String PASSWORD_SIZE_MESSAGE = "La contraseña no puede superar los 64 caracteres.";

    @JsonProperty("correo")
    @NotBlank(message = EMAIL_MESSAGE)
    @Email(message = EMAIL_MESSAGE)
    @Size(max = 100, message = EMAIL_MESSAGE)
    private String correo;

    @JsonProperty("contrasena")
    @NotBlank(message = PASSWORD_MESSAGE)
    @Size(max = 64, message = PASSWORD_SIZE_MESSAGE)
    private String contrasena;

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        if (correo == null) {
            this.correo = null;
        } else {
            this.correo = correo.trim().toLowerCase(Locale.ROOT);
        }
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    /**
     * RG-04 / Se rechazan (HTTP 400) los campos no definidos en el contrato (en especial
     * roles, estado o id enviados por el usuario en registro y perfil).
     */
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Campo no permitido: " + name);
    }
}
