package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/auth/password/forgot (RF-MS01-08, form F-04), any field outside the contract is rejected (RG-04)
 */
public class ForgotPasswordRequest extends StrictRequest {

    @JsonProperty("correo")
    @NotBlank(message = FieldRules.EMAIL_RECOVERY_MESSAGE)
    @Email(message = FieldRules.EMAIL_RECOVERY_MESSAGE)
    @Size(max = FieldRules.EMAIL_MAX_LENGTH, message = FieldRules.EMAIL_RECOVERY_MESSAGE)
    private String email;

        /**
     * Returns the email to which the recovery code will be sent
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
}
