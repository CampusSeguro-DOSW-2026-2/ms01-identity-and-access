package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/v1/auth/login (formulario F-02, esquema LoginRequest del contrato).
 *
 * <ul>
 *   <li>RG-01: el correo se recorta y se pasa a minusculas; la contrasena NO se recorta ni se normaliza.</li>
 *   <li>RG-02: un campo obligatorio vacio o solo con espacios se considera faltante.</li>
 *   <li>RG-04: cualquier campo no definido en el contrato se rechaza (HTTP 400).</li>
 *   <li>En el login no se revalida la politica de contrasena (solo no vacia, maximo 64).</li>
 * </ul>
 *
 * Los nombres de las propiedades coinciden con los del JSON para que los errores de validacion
 * reporten el campo tal como lo conoce el cliente.
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
        this.correo = (correo == null) ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    /**
     * RG-04 / additionalProperties: false. Cualquier propiedad desconocida hace fallar la
     * deserializacion, lo que el GlobalExceptionHandler traduce a HTTP 400.
     */
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Campo no permitido: " + name);
    }
}
