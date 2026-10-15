package edu.eci.dosw.login.dto;

import edu.eci.dosw.login.entity.UserRol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Actualizacion parcial de un usuario (PATCH). Todos los campos son opcionales.
 */
@Schema(description = "Datos para la actualizacion parcial de un usuario")
public record UserPatchRequest(

        @Schema(description = "Nuevo correo electronico", example = "nuevo@mail.escuelaing.edu.co")
        @Email(message = "El correo debe tener un formato valido")
        @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
        String email,

        @Schema(description = "Nueva contrasena", example = "Secreta456")
        @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
        String password,

        @Schema(description = "Nuevo rol", example = "PROFESOR")
        UserRol rol
) {
}