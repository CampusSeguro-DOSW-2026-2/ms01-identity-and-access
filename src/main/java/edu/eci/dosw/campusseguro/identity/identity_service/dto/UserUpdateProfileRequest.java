package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/v1/users/me (RF-MS01-06, form F-03), only the names and lastnames are edited, the email, the roles,
 * the status and the id are rejected with HTTP 400 (RN-MS01-15, RN-MS01-16, RG-04)
 */
public class UserUpdateProfileRequest extends StrictRequest {

    public static final String EMAIL_NOT_EDITABLE_MESSAGE = "El correo no se puede modificar.";
    public static final String ROLES_NOT_EDITABLE_MESSAGE = "Los roles no se pueden modificar desde el perfil.";
    public static final String STATE_NOT_EDITABLE_MESSAGE = "El estado no se puede modificar desde el perfil.";

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

        /**
     * Explains why the email, the roles and the status are rejected, the rest of the undefined fields use the general
     * message
     *
     * @param fieldName name of the undefined property
     * @return the error message of that property
     */
    @Override
    public String messageForUnknownField(String fieldName) {
        if ("correo".equals(fieldName)) {
            return EMAIL_NOT_EDITABLE_MESSAGE;
        }
        if ("roles".equals(fieldName)) {
            return ROLES_NOT_EDITABLE_MESSAGE;
        }
        if ("estado".equals(fieldName)) {
            return STATE_NOT_EDITABLE_MESSAGE;
        }
        return super.messageForUnknownField(fieldName);
    }

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
}
