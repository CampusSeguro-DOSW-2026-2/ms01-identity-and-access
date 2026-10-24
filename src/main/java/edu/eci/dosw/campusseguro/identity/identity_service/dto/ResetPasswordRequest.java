package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

    @NotBlank(message = "Ingrese un correo válido.")
    @Email(message = "Ingrese un correo válido.")
    @Size(max = 100, message = "Ingrese un correo válido.")
    String correo,

    @NotBlank(message = "Código incorrecto o vencido.")
    @Pattern(regexp = "^\\d{6}$", message = "Código incorrecto o vencido.")
    String codigo,

    @NotBlank(message = "La contraseña debe tener 8 a 64 caracteres, con mayúscula, minúscula, número y carácter especial.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,64}$",
        message = "La contraseña debe tener 8 a 64 caracteres, con mayúscula, minúscula, número y carácter especial."
    )
    String nueva_contrasena,

    @NotBlank(message = "Las contraseñas no coinciden.")
    String confirmar_contrasena

) {}