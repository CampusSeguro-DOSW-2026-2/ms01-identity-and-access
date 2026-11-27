package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PasswordService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Password recovery and change (RF-MS01-08), the request and the reset with a code are public because they are used
 * without a session, the password change requires the identity that the API Gateway injects in the headers
 */
@RestController
@RequestMapping("/api/v1")
public class PasswordController {

    public static final String FORGOT_MESSAGE = "Si el correo está registrado, enviamos un código de recuperación.";
    public static final String RESET_MESSAGE = "Contraseña actualizada correctamente. Inicia sesión de nuevo.";
    public static final String CHANGE_MESSAGE = "Contraseña actualizada correctamente.";

    private final PasswordService passwordService;

        /**
     * Creates the controller with its service
     *
     * @param passwordService password recovery and change service
     */
    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

        /**
     * POST /api/v1/auth/password/forgot: requests the password recovery code (RF-MS01-08, form F-04)
     *
     * 200 always with the same message, whether the email exists or not (RN-MS01-20), 400 email with an invalid format
     *
     * @param request email of the account to be recovered
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the single confirmation message
     */
    @PostMapping("/auth/password/forgot")
    public ResponseEntity<MessageResponse> forgot(@RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordService.requestRecoveryCode(request, CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(new MessageResponse(FORGOT_MESSAGE));
    }

        /**
     * POST /api/v1/auth/password/reset: resets the password with the code received by email (RF-MS01-08, form F-05)
     *
     * 200 password updated, 400 invalid data or incorrect, expired or already used code with a generic message
     *
     * @param request email, code and new password with its confirmation
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the confirmation message
     */
    @PostMapping("/auth/password/reset")
    public ResponseEntity<MessageResponse> reset(@RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordService.resetPassword(request, CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(new MessageResponse(RESET_MESSAGE));
    }

        /**
     * PUT /api/v1/users/me/password: changes the password of the authenticated user (RF-MS01-08, form F-06)
     *
     * 200 password updated, 400 invalid data, incorrect current password or new password equal to the current one, 401
     * missing identity, 403 no permission, 404 non-existent user
     *
     * @param requester identity of the requester taken from the headers
     * @param request current password, new password and its confirmation
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the confirmation message
     */
    @PutMapping("/users/me/password")
    public ResponseEntity<MessageResponse> change(RequesterIdentity requester,
            @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        passwordService.changePassword(requester, request, CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(new MessageResponse(CHANGE_MESSAGE));
    }
}
