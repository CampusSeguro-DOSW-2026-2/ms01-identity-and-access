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
 * Cuerpo de POST /api/v1/auth/password/reset (RF-MS01-08, formulario F-05), cualquier campo fuera del
 * contrato se rechaza (RG-04)
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
     * Devuelve el correo de la cuenta cuya contraseña se restablece
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
     * Devuelve el código de recuperación recibido por correo
     *
     * @return el código sin espacios laterales, null si no se envió
     */
    public String getCode() {
        return this.code;
    }

    /**
     * Coloca el código de recuperación, recortado según RG-01
     *
     * @param code código recibido
     */
    public void setCode(String code) {
        this.code = Texts.trimToNull(code);
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
