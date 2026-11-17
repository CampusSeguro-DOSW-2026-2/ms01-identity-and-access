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
 * Body of POST /api/v1/auth/register (RF-MS01-01, form F-01), the role and the status are never chosen by whoever
 * registers and any field outside the contract is rejected (RG-04)
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
    @Size(min = FieldRules.OPTIONAL_NAME_MIN_LENGTH, max = FieldRules.NAME_MAX_LENGTH,
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
    @Size(min = FieldRules.OPTIONAL_NAME_MIN_LENGTH, max = FieldRules.NAME_MAX_LENGTH,
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
     * Returns the first name
     *
     * @return the first name without surrounding spaces, null if it was not sent
     */
    public String getFirstName() {
        return this.firstName;
    }

        /**
     * Sets the first name, trimmed according to RG-01
     *
     * @param name first name received
     */
    public void setFirstName(String name) {
        this.firstName = Texts.trimToNull(name);
    }

        /**
     * Returns the second name
     *
     * @return the second name without surrounding spaces, null if it was not sent
     */
    public String getSecondName() {
        return this.secondName;
    }

        /**
     * Sets the second name, trimmed according to RG-01 and omitted if it is left empty
     *
     * @param name second name received
     */
    public void setSecondName(String name) {
        this.secondName = Texts.trimToNull(name);
    }

        /**
     * Returns the first lastname
     *
     * @return the first lastname without surrounding spaces, null if it was not sent
     */
    public String getFirstLastName() {
        return this.firstLastName;
    }

        /**
     * Sets the first lastname, trimmed according to RG-01
     *
     * @param lastName first lastname received
     */
    public void setFirstLastName(String lastName) {
        this.firstLastName = Texts.trimToNull(lastName);
    }

        /**
     * Returns the second lastname
     *
     * @return the second lastname without surrounding spaces, null if it was not sent
     */
    public String getSecondLastName() {
        return this.secondLastName;
    }

        /**
     * Sets the second lastname, trimmed according to RG-01 and omitted if it is left empty
     *
     * @param lastName second lastname received
     */
    public void setSecondLastName(String lastName) {
        this.secondLastName = Texts.trimToNull(lastName);
    }

        /**
     * Returns the institutional email
     *
     * @return the email trimmed and in lowercase, null if it was not sent
     */
    public String getEmail() {
        return this.email;
    }

        /**
     * Sets the email, trimmed and normalized to lowercase according to RG-01
     *
     * @param email email received
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
     * Returns the password as it was received
     *
     * @return the password, null if it was not sent
     */
    public String getPassword() {
        return this.password;
    }

        /**
     * Sets the password, which is neither trimmed nor normalized according to RG-01
     *
     * @param password password received
     */
    public void setPassword(String password) {
        this.password = password;
    }

        /**
     * Returns the confirmation of the password as it was received
     *
     * @return the confirmation, null if it was not sent
     */
    public String getPasswordConfirmation() {
        return this.passwordConfirmation;
    }

        /**
     * Sets the confirmation of the password, which is neither trimmed nor normalized according to RG-01
     *
     * @param passwordConfirmation confirmation received
     */
    public void setPasswordConfirmation(String passwordConfirmation) {
        this.passwordConfirmation = passwordConfirmation;
    }
}
