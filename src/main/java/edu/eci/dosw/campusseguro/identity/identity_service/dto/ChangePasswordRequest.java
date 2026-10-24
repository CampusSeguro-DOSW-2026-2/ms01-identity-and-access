package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RF-MS01-08: cambio de contrasena estando autenticado.
 */
public record ChangePasswordRequest(

    @NotBlank(message = "La contraseña actual es incorrecta.")
    @Size(max = 64, message = "La contraseña actual es incorrecta.")
    String contrasena_actual,

    @NotBlank(message = "La nueva contraseña no cumple la política o es igual a la actual.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,64}$",
        message = "La nueva contraseña no cumple la política o es igual a la actual."
    )
    String nueva_contrasena,

    @NotBlank(message = "Las contraseñas no coinciden.")
    String confirmar_contrasena

) {}