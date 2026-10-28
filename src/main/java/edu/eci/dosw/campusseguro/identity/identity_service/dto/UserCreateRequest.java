package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldsMatch;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/v1/auth/register (RF-MS01-01, formulario F-01), el rol y el estado nunca los
 * elige quien se registra y cualquier campo fuera del contrato se rechaza (RG-04)
 */
@FieldsMatch(field = "password", matchField = "passwordConfirmation",
        message = FieldRules.PASSWORDS_MISMATCH_MESSAGE)
public class UserCreateRequest extends StrictRequest {

    @JsonProperty("primer_nombre")
    @NotBlank(message = FieldRules.FIRST_NAME_MESSAGE)
    @Size(min = FieldRules.NAME_MIN_LENGTH, max = FieldRules.NAME_MAX_LENGTH,
            message = FieldRules.FIRST_NAME_MESSAGE)
    @Pattern(regexp = FieldRules.NAME_PATTERN, message = FieldRules.FIRST_NAME_MESSAGE)
    private String firstName;

    @JsonProperty("segundo_nombre")
    @Size(min = FieldRules.NAME_MIN_LENGTH, max = FieldRules.NAME_MAX_LENGTH,
            message = FieldRules.SECOND_NAME_MESSAGE)
    @Pattern(regexp = FieldRules.NAME_PATTERN, message = FieldRules.SECOND_NAME_MESSAGE)
    private String secondName;

    @JsonProperty("primer_apellido")
    @NotBlank(message = FieldRules.FIRST_LAST_NAME_MESSAGE)
    @Size(min = FieldRules.NAME_MIN_LENGTH, max = FieldRules.NAME_MAX_LENGTH,
            message = FieldRules.FIRST_LAST_NAME_MESSAGE)
    @Pattern(regexp = FieldRules.NAME_PATTERN, message = FieldRules.FIRST_LAST_NAME_MESSAGE)
    private String firstLastName;

    @JsonProperty("segundo_apellido")
    @Size(min = FieldRules.NAME_MIN_LENGTH, max = FieldRules.NAME_MAX_LENGTH,
            message = FieldRules.SECOND_LAST_NAME_MESSAGE)
    @Pattern(regexp = FieldRules.NAME_PATTERN, message = FieldRules.SECOND_LAST_NAME_MESSAGE)
    private String secondLastName;

    @JsonProperty("correo")
    @NotBlank(message = FieldRules.EMAIL_REGISTER_MESSAGE)
    @Email(message = FieldRules.EMAIL_REGISTER_MESSAGE)
    @Size(max = FieldRules.EMAIL_MAX_LENGTH, message = FieldRules.EMAIL_REGISTER_MESSAGE)
    private String email;

    @JsonProperty("contrasena")
    @NotBlank(message = FieldRules.PASSWORD_MESSAGE)
    @Pattern(regexp = FieldRules.PASSWORD_PATTERN, message = FieldRules.PASSWORD_MESSAGE)
    private String password;

    @JsonProperty("confirmar_contrasena")
    @NotBlank(message = FieldRules.PASSWORDS_MISMATCH_MESSAGE)
    private String passwordConfirmation;

    /**
     * Devuelve el primer nombre
     *
     * @return el primer nombre sin espacios laterales, null si no se envió
     */
    public String getFirstName() {
        return this.firstName;
    }

    /**
     * Coloca el primer nombre, recortado según RG-01
     *
     * @param name primer nombre recibido
     */
    public void setFirstName(String name) {
        this.firstName = Texts.trimToNull(name);
    }

    /**
     * Devuelve el segundo nombre
     *
     * @return el segundo nombre sin espacios laterales, null si no se envió
     */
    public String getSecondName() {
        return this.secondName;
    }

    /**
     * Coloca el segundo nombre, recortado según RG-01 y omitido si queda vacío
     *
     * @param name segundo nombre recibido
     */
    public void setSecondName(String name) {
        this.secondName = Texts.trimToNull(name);
    }

    /**
     * Devuelve el primer apellido
     *
     * @return el primer apellido sin espacios laterales, null si no se envió
     */
    public String getFirstLastName() {
        return this.firstLastName;
    }

    /**
     * Coloca el primer apellido, recortado según RG-01
     *
     * @param lastName primer apellido recibido
     */
    public void setFirstLastName(String lastName) {
        this.firstLastName = Texts.trimToNull(lastName);
    }

    /**
     * Devuelve el segundo apellido
     *
     * @return el segundo apellido sin espacios laterales, null si no se envió
     */
    public String getSecondLastName() {
        return this.secondLastName;
    }

    /**
     * Coloca el segundo apellido, recortado según RG-01 y omitido si queda vacío
     *
     * @param lastName segundo apellido recibido
     */
    public void setSecondLastName(String lastName) {
        this.secondLastName = Texts.trimToNull(lastName);
    }

    /**
     * Devuelve el correo institucional
     *
     * @return el correo recortado y en minúsculas, null si no se envió
     */
    public String getEmail() {
        return this.email;
    }

    /**
     * Coloca el correo, recortado y normalizado a minúsculas según RG-01
     *
     * @param email correo recibido
     */
    public void setEmail(String email) {
        String trimmed = Texts.trimToNull(email);
        if (trimmed == null) {
            this.email = null;
        } else {
            this.email = trimmed.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Devuelve la contraseña tal como se recibió
     *
     * @return la contraseña, null si no se envió
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * Coloca la contraseña, que no se recorta ni se normaliza según RG-01
     *
     * @param password contraseña recibida
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Devuelve la confirmación de la contraseña tal como se recibió
     *
     * @return la confirmación, null si no se envió
     */
    public String getPasswordConfirmation() {
        return this.passwordConfirmation;
    }

    /**
     * Coloca la confirmación de la contraseña, que no se recorta ni se normaliza según RG-01
     *
     * @param passwordConfirmation confirmación recibida
     */
    public void setPasswordConfirmation(String passwordConfirmation) {
        this.passwordConfirmation = passwordConfirmation;
    }
}
