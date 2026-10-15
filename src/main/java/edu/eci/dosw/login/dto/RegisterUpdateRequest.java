package edu.eci.dosw.login.dto;

import edu.eci.dosw.login.entity.UserRol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Actualizacion completa de un usuario (PUT). Todos los campos son obligatorios.
 */
@Schema(description = "Datos para la actualizacion completa de un usuario")
public record RegisterUpdateRequest(

        @Schema(description = "Correo electronico", example = "jdoe@mail.escuelaing.edu.co")
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato valido")
        @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
        String email,

        @Schema(description = "Nueva contrasena", example = "Secreta456")
        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
        String password,

        @Schema(description = "Nuevo rol", example = "PROFESOR")
        @NotNull(message = "El rol es obligatorio")
        UserRol rol
) {
}