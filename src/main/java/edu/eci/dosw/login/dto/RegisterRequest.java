package edu.eci.dosw.login.dto;

import edu.eci.dosw.login.entity.UserRol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada para registrar un nuevo usuario.
 */
@Schema(description = "Datos de registro de un usuario")
public record RegisterRequest(

        @Schema(description = "Correo electronico unico", example = "jdoe@mail.escuelaing.edu.co")
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato valido")
        @Size(max = 120, message = "El correo no puede superar los 120 caracteres")
        String email,

        @Schema(description = "Contrasena (se almacena cifrada)", example = "Secreta123")
        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
        String password,

        @Schema(description = "Rol del usuario", example = "ESTUDIANTE")
        @NotNull(message = "El rol es obligatorio")
        UserRol rol
) {
}