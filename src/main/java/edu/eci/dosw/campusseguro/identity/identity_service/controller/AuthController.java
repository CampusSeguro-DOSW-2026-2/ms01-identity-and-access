package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RegistrationAcceptedResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResendRegistrationCodeRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.VerifyRegistrationRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RegistrationService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Public endpoints for registration (RF-MS01-01) and authentication (RF-MS01-02), they do not require the identity
 * headers because they are invoked before having a session
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REGISTER_MESSAGE =
            "Enviamos un código de verificación a su correo. Ingréselo para crear la cuenta.";
    public static final String RESEND_MESSAGE =
            "Si hay un registro pendiente para este correo, enviamos un nuevo código de verificación.";

    private final RegistrationService registrationService;
    private final AuthService authService;

        /**
     * Creates the controller with its services
     *
     * @param registrationService user registration service
     * @param authService authentication service
     */
    public AuthController(RegistrationService registrationService, AuthService authService) {
        this.registrationService = registrationService;
        this.authService = authService;
    }

        /**
     * POST /api/v1/auth/register: receives the registration of an institutional user and sends a verification code to
     * the email, the user is not created yet (RF-MS01-01)
     *
     * 202 code sent and registration pending, with the registration token that the verification requires, 400 invalid
     * data, 409 email already registered
     *
     * @param request data of the registration form
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the confirmation message and the registration token
     */
    @PostMapping("/register")
    public ResponseEntity<RegistrationAcceptedResponse> register(@RequestBody UserCreateRequest request,
            HttpServletRequest httpRequest) {
        String token = registrationService.requestRegistration(request, CorrelationIds.current(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new RegistrationAcceptedResponse(REGISTER_MESSAGE, token));
    }

        /**
     * POST /api/v1/auth/register/verify: confirms the code sent to the email and creates the user (RF-MS01-01)
     *
     * 201 user created, 400 invalid data or incorrect or expired code, 409 email already registered
     *
     * @param request email and code
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the created user
     */
    @PostMapping("/register/verify")
    public ResponseEntity<UserResponse> verify(@RequestBody VerifyRegistrationRequest request,
            HttpServletRequest httpRequest) {
        UserResponse created = registrationService.verify(request, CorrelationIds.current(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

        /**
     * POST /api/v1/auth/register/resend: sends a new verification code to an email with a pending registration
     * (RF-MS01-01), the response is always the same so it does not reveal whether the registration exists
     *
     * 200 fixed message, 400 invalid email
     *
     * @param request email of the pending registration
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the fixed message
     */
    @PostMapping("/register/resend")
    public ResponseEntity<MessageResponse> resend(@RequestBody ResendRegistrationCodeRequest request,
            HttpServletRequest httpRequest) {
        registrationService.resendCode(request, CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(new MessageResponse(RESEND_MESSAGE));
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
