package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de PUT /api/v1/users/me/password (RF-MS01-08, formulario F-06), cualquier campo fuera del
 * contrato se rechaza (RG-04)
 */
@FieldsMatch(field = "newPassword", matchField = "newPasswordConfirmation",
        message = FieldRules.PASSWORDS_MISMATCH_MESSAGE)
public class ChangePasswordRequest extends StrictRequest {

    public static final String CURRENT_PASSWORD_MESSAGE = "La contraseña actual es incorrecta.";
    public static final String NEW_PASSWORD_MESSAGE =
            "La nueva contraseña no cumple la política o es igual a la actual.";

    @JsonProperty("contrasena_actual")
    @NotBlank(message = CURRENT_PASSWORD_MESSAGE)
    @Size(max = FieldRules.PASSWORD_MAX_LENGTH, message = CURRENT_PASSWORD_MESSAGE)
    private String currentPassword;

    @JsonProperty("nueva_contrasena")
    @NotBlank(message = NEW_PASSWORD_MESSAGE)
    @Pattern(regexp = FieldRules.PASSWORD_PATTERN, message = NEW_PASSWORD_MESSAGE)
    private String newPassword;

    @JsonProperty("confirmar_nueva_contrasena")
    @NotBlank(message = FieldRules.PASSWORDS_MISMATCH_MESSAGE)
    private String newPasswordConfirmation;

    /**
     * Devuelve la contraseña actual tal como se recibió
     *
     * @return la contraseña actual, null si no se envió
     */
    public String getCurrentPassword() {
        return this.currentPassword;
    }

    /**
     * Coloca la contraseña actual, que no se recorta ni se normaliza según RG-01
     *
     * @param currentPassword contraseña actual recibida
     */
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    /**
     * Devuelve la nueva contraseña tal como se recibió
     *
     * @return la nueva contraseña, null si no se envió
     */
    public String getNewPassword() {
        return this.newPassword;
    }

    /**
     * Coloca la nueva contraseña, que no se recorta ni se normaliza según RG-01
     *
     * @param newPassword nueva contraseña recibida
     */
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    /**
     * Devuelve la confirmación de la nueva contraseña tal como se recibió
     *
     * @return la confirmación, null si no se envió
     */
    public String getNewPasswordConfirmation() {
        return this.newPasswordConfirmation;
    }

    /**
     * Coloca la confirmación de la nueva contraseña, que no se recorta ni se normaliza según RG-01
     *
     * @param newPasswordConfirmation confirmación recibida
     */
    public void setNewPasswordConfirmation(String newPasswordConfirmation) {
        this.newPasswordConfirmation = newPasswordConfirmation;
    }
}
