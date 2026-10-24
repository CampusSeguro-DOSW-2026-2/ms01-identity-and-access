package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(

    @NotBlank(message = "Ingrese un correo válido.")
    @Email(message = "Ingrese un correo válido.")
    @Size(max = 100, message = "Ingrese un correo válido.")
    String correo

) {}