package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private UserService userService;
    private AuthService authService;

    public AuthController(UserService userService, AuthService authService){
        this.userService = userService;
        this.authService = authService;
    }

        /**
     *
     * @param request
     * @return
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request){
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * RF-MS01-02 Autenticar usuario (login). Endpoint publico.
     *
     * 200: token JWT, tipo, expiracion y datos basicos. 400: datos invalidos. 401: credenciales
     * incorrectas (identico para correo inexistente y contrasena erronea). 403: cuenta inactiva.
     *
     * @param request       correo y contrasena
     * @param correlationId cabecera X-Correlation-ID inyectada por el API Gateway (opcional)
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId){
        return ResponseEntity.ok(authService.login(request, correlationId));
    }
}
