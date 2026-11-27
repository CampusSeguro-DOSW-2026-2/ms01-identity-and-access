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
 * Body of POST /api/v1/auth/password/reset (RF-MS01-08, form F-05), any field outside the contract is rejected (RG-04)
 */
@FieldsMatch(field = "newPassword", matchField = "newPasswordConfirmation",
        message = FieldRules.PASSWORDS_MISMATCH_MESSAGE)
public class ResetPasswordRequest extends StrictRequest {

    @JsonProperty("correo")
    @NotBlank(message = FieldRules.EMAIL_RECOVERY_MESSAGE)
    @Email(message = FieldRules.EMAIL_RECOVERY_MESSAGE)
    @Size(max = FieldRules.EMAIL_MAX_LENGTH, message = FieldRules.EMAIL_RECOVERY_MESSAGE)
    private String email;

    @JsonProperty("codigo")
    @NotBlank(message = FieldRules.RECOVERY_CODE_MESSAGE)
    @Pattern(regexp = FieldRules.RECOVERY_CODE_PATTERN, message = FieldRules.RECOVERY_CODE_MESSAGE)
    private String code;

    @JsonProperty("nueva_contrasena")
    @NotBlank(message = FieldRules.PASSWORD_MESSAGE)
    @Pattern(regexp = FieldRules.PASSWORD_PATTERN, message = FieldRules.PASSWORD_MESSAGE)
    private String newPassword;

    @JsonProperty("confirmar_nueva_contrasena")
    @NotBlank(message = FieldRules.PASSWORDS_MISMATCH_MESSAGE)
    private String newPasswordConfirmation;

        /**
     * Returns the email of the account whose password is reset
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
     * Returns the recovery code received by email
     *
     * @return the code without surrounding spaces, null if it was not sent
     */
    public String getCode() {
        return this.code;
    }

        /**
     * Sets the recovery code, trimmed according to RG-01
     *
     * @param code code received
     */
    public void setCode(String code) {
        this.code = Texts.trimToNull(code);
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
