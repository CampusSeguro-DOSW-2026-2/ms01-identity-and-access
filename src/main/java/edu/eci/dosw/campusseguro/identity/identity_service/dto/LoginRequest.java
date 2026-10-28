package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/v1/auth/login (RF-MS01-02, formulario F-02), cualquier campo fuera del contrato
 * se rechaza (RG-04)
 */
public class LoginRequest extends StrictRequest {

    public static final String EMAIL_MESSAGE = "Ingrese un correo válido.";
    public static final String PASSWORD_MESSAGE = "Ingrese su contraseña.";
    public static final String PASSWORD_SIZE_MESSAGE = "La contraseña no puede superar los 64 caracteres.";

    @JsonProperty("correo")
    @NotBlank(message = EMAIL_MESSAGE)
    @Email(message = EMAIL_MESSAGE)
    @Size(max = FieldRules.EMAIL_MAX_LENGTH, message = EMAIL_MESSAGE)
    private String correo;

    @JsonProperty("contrasena")
    @NotBlank(message = PASSWORD_MESSAGE)
    @Size(max = FieldRules.PASSWORD_MAX_LENGTH, message = PASSWORD_SIZE_MESSAGE)
    private String contrasena;

    /**
     * Devuelve el correo con el que se intenta iniciar sesión
     *
     * @return el correo recortado y en minúsculas, null si no se envió
     */
    public String getCorreo() {
        return correo;
    }

    /**
     * Coloca el correo, recortado y normalizado a minúsculas según RG-01
     *
     * @param correo correo recibido
     */
    public void setCorreo(String correo) {
        String trimmed = Texts.trimToNull(correo);
        if (trimmed == null) {
            this.correo = null;
        } else {
            this.correo = trimmed.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Devuelve la contraseña tal como se recibió
     *
     * @return la contraseña, null si no se envió
     */
    public String getContrasena() {
        return contrasena;
    }

    /**
     * Coloca la contraseña, que no se recorta ni se normaliza según RG-01
     *
     * @param contrasena contraseña recibida
     */
    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }
}
