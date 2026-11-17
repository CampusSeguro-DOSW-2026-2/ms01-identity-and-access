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
 * Public endpoints for registration (RF-MS01-01) and authentication (RF-MS01-02), they do not require the identity
 * headers because they are invoked before having a session
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

        /**
     * Creates the controller with its services
     *
     * @param userService user registration service
     * @param authService authentication service
     */
    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

        /**
     * POST /api/v1/auth/register: registers an institutional user (RF-MS01-01)
     *
     * 201 user created, 400 invalid data, 409 email already registered
     *
     * @param request data of the registration form
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the created user
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody UserCreateRequest request,
            HttpServletRequest httpRequest) {
        UserResponse created = userService.create(request, CorrelationIds.current(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

        /**
     * POST /api/v1/auth/login: authenticates a user and issues their JWT token (RF-MS01-02)
     *
     * 200 token with its type, expiration and basic data, 400 invalid data, 401 incorrect credentials, identical for a
     * non-existent email and a wrong password, 403 inactive account
     *
     * @param request email and password
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the token and the basic data of the user
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, CorrelationIds.current(httpRequest)));
    }
}
