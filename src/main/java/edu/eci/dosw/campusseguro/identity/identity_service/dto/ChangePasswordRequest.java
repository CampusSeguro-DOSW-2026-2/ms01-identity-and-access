package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/v1/users/me/password (RF-MS01-08, form F-06), any field outside the contract is rejected (RG-04)
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
     * Returns the current password as it was received
     *
     * @return the current password, null if it was not sent
     */
    public String getCurrentPassword() {
        return this.currentPassword;
    }

        /**
     * Sets the current password, which is neither trimmed nor normalized according to RG-01
     *
     * @param currentPassword current password received
     */
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

        /**
     * Returns the new password as it was received
     *
     * @return the new password, null if it was not sent
     */
    public String getNewPassword() {
        return this.newPassword;
    }

        /**
     * Sets the new password, which is neither trimmed nor normalized according to RG-01
     *
     * @param newPassword new password received
     */
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

        /**
     * Returns the confirmation of the new password as it was received
     *
     * @return the confirmation, null if it was not sent
     */
    public String getNewPasswordConfirmation() {
        return this.newPasswordConfirmation;
    }

        /**
     * Sets the confirmation of the new password, which is neither trimmed nor normalized according to RG-01
     *
     * @param newPasswordConfirmation confirmation received
     */
    public void setNewPasswordConfirmation(String newPasswordConfirmation) {
        this.newPasswordConfirmation = newPasswordConfirmation;
    }
}
