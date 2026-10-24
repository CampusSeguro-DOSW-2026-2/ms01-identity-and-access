package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PasswordService;
import jakarta.validation.Valid;

/**
 * RF-MS01-08 Recuperar contrasena (flujo A). Endpoints publicos (RN-MS01-23).
 */
@RestController
@RequestMapping("/api/v1/auth/password")
public class PasswordController {

    private final PasswordService passwordService;

    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    /**
     * Solicita el codigo de recuperacion. Siempre responde 200 con el mismo mensaje (RN-MS01-20).
     *
     * @param request       correo de la cuenta
     * @param correlationId cabecera X-Correlation-ID inyectada por el API Gateway (opcional)
     */
    @PostMapping("/forgot")
    public ResponseEntity<MessageResponse> forgot(
            @Valid @RequestBody ForgotPasswordRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ResponseEntity.ok(passwordService.requestResetCode(request, correlationId));
    }

    /**
     * Restablece la contrasena con el codigo recibido por correo.
     *
     * @param request       correo, codigo y nueva contrasena con su confirmacion
     * @param correlationId cabecera X-Correlation-ID inyectada por el API Gateway (opcional)
     */
    @PostMapping("/reset")
    public ResponseEntity<MessageResponse> reset(
            @Valid @RequestBody ResetPasswordRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ResponseEntity.ok(passwordService.resetPassword(request, correlationId));
    }
}