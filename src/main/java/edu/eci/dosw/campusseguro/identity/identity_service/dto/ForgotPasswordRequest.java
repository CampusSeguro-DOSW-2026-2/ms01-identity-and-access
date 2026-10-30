package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/v1/auth/password/forgot (RF-MS01-08, formulario F-04), cualquier campo fuera del
 * contrato se rechaza (RG-04)
 */
public class ForgotPasswordRequest extends StrictRequest {

    @JsonProperty("correo")
    @NotBlank(message = FieldRules.EMAIL_RECOVERY_MESSAGE)
    @Email(message = FieldRules.EMAIL_RECOVERY_MESSAGE)
    @Size(max = FieldRules.EMAIL_MAX_LENGTH, message = FieldRules.EMAIL_RECOVERY_MESSAGE)
    private String email;

    /**
     * Devuelve el correo al que se enviará el código de recuperación
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
}
