package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/auth/login (RF-MS01-02, form F-02), any field outside the contract is rejected (RG-04)
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
     * Returns the email used to try to log in
     *
     * @return the email trimmed and in lowercase, null if it was not sent
     */
    public String getCorreo() {
        return correo;
    }

        /**
     * Sets the email, trimmed and normalized to lowercase according to RG-01
     *
     * @param correo email received
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
     * Returns the password as it was received
     *
     * @return the password, null if it was not sent
     */
    public String getContrasena() {
        return contrasena;
    }

        /**
     * Sets the password, which is neither trimmed nor normalized according to RG-01
     *
     * @param contrasena password received
     */
    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }
}
