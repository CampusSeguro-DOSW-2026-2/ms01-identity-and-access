package edu.eci.dosw.login.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada para iniciar sesion.
 */
@Schema(description = "Credenciales de acceso")
public record LoginRequest(

        @Schema(description = "Correo electronico del usuario", example = "jdoe@mail.escuelaing.edu.co")
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato valido")
        @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
        String email,

        @Schema(description = "Contrasena del usuario", example = "Secreta123")
        @NotBlank(message = "La contrasena es obligatoria")
        String password
) {
}