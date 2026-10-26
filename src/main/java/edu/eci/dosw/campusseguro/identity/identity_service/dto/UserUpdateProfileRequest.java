package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de PUT /api/v1/users/me (RF-MS01-06, formulario F-03), solo se editan los nombres y apellidos,
 * el correo, los roles, el estado y el id se rechazan con HTTP 400 (RN-MS01-15, RN-MS01-16, RG-04)
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

    /**
     * Explica por qué se rechazan el correo, los roles y el estado, el resto de campos no definidos
     * usan el mensaje general
     *
     * @param fieldName nombre de la propiedad no definida
     * @return el mensaje de error de esa propiedad
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
}
