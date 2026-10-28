package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Endpoints públicos de registro (RF-MS01-01) y autenticación (RF-MS01-02), no requieren las cabeceras de
 * identidad porque se invocan antes de tener una sesión
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    /**
     * Crea el controlador con sus servicios
     *
     * @param userService servicio de registro de usuarios
     * @param authService servicio de autenticación
     */
    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/register: registra un usuario institucional (RF-MS01-01)
     *
     * 201 usuario creado, 400 datos inválidos, 409 correo ya registrado
     *
     * @param request datos del formulario de registro
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el usuario creado
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody UserCreateRequest request,
            HttpServletRequest httpRequest) {
        UserResponse created = userService.create(request, CorrelationIds.current(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * POST /api/v1/auth/login: autentica a un usuario y emite su token JWT (RF-MS01-02)
     *
     * 200 token con su tipo, expiración y datos básicos, 400 datos inválidos, 401 credenciales incorrectas,
     * idéntico para correo inexistente y contraseña errónea, 403 cuenta inactiva
     *
     * @param request correo y contraseña
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el token y los datos básicos del usuario
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, CorrelationIds.current(httpRequest)));
    }
}
