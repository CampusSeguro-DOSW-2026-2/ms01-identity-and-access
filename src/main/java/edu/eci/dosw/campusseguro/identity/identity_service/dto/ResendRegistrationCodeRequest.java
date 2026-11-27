package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/auth/register/resend (RF-MS01-01, form F-14), any field outside the contract is rejected
 * (RG-04)
 */
public class ResendRegistrationCodeRequest extends StrictRequest {

    @JsonProperty("correo")
    @NotBlank(message = FieldRules.EMAIL_VERIFICATION_MESSAGE)
    @Email(message = FieldRules.EMAIL_VERIFICATION_MESSAGE)
    @Size(max = FieldRules.EMAIL_MAX_LENGTH, message = FieldRules.EMAIL_VERIFICATION_MESSAGE)
    private String email;

    @JsonProperty("token_registro")
    @NotBlank(message = FieldRules.VERIFICATION_CODE_MESSAGE)
    @Size(max = FieldRules.REGISTRATION_TOKEN_MAX_LENGTH, message = FieldRules.VERIFICATION_CODE_MESSAGE)
    private String token;

        /**
     * Returns the email to which a new verification code is sent
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
     * Returns the registration token received in the response of the registration
     *
     * @return the token without surrounding spaces, null if it was not sent
     */
    public String getToken() {
        return this.token;
    }

        /**
     * Sets the registration token, trimmed according to RG-01
     *
     * @param token token received
     */
    public void setToken(String token) {
        this.token = Texts.trimToNull(token);
    }
}
