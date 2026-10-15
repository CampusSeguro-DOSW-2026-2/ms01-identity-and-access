package edu.eci.dosw.login.controller;

import edu.eci.dosw.login.dto.LoginRequest;
import edu.eci.dosw.login.dto.LoginResponse;
import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;
import edu.eci.dosw.login.service.AccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacion y registro.
 *
 * FASE 1 (MVP): sin JWT. {@code /me} recibe el correo por la cabecera
 * {@code X-Email}; en la FASE 2 se obtendra del token.
 */
@Tag(name = "Autenticacion", description = "Registro, login y perfil del usuario (MVP sin JWT)")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccessService accessService;

    public AuthController(AccessService accessService) {
        this.accessService = accessService;
    }

    @Operation(summary = "Registrar un nuevo usuario")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return accessService.register(request);
    }

    @Operation(summary = "Iniciar sesion (MVP: aun no emite JWT)")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return accessService.login(request);
    }

    @Operation(summary = "Consultar el perfil del usuario actual")
    @GetMapping("/me")
    public RegisterResponse me(@RequestHeader("X-Email") String email) {
        return accessService.findByEmail(email);
    }
}