package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PasswordService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationId;
import edu.eci.dosw.campusseguro.identity.identity_service.util.IdentityHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * RF-MS01-08 Recuperar contrasena (flujo A, endpoints publicos) y cambiarla estando autenticado
 * (flujo B, identidad por cabecera X-User-Id). Toda respuesta devuelve X-Correlation-ID.
 */
@RestController
@RequestMapping("/api/v1")
public class PasswordController {

    /** x-permiso ms01:password:change: lo tienen los 8 roles del catalogo (swagger.yml). */
    private static final Set<UserRole> CHANGE_PASSWORD_ROLES = EnumSet.allOf(UserRole.class);

    private final PasswordService passwordService;

    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    /**
     * Solicita el codigo de recuperacion. Siempre responde 200 con el mismo mensaje (RN-MS01-20).
     *
     * @param request correo de la cuenta
     * @param http    peticion HTTP, de donde se resuelve X-Correlation-ID
     */
    @PostMapping("/auth/password/forgot")
    public ResponseEntity<MessageResponse> forgot(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest http) {
        String correlationId = CorrelationId.from(http);
        return ok(passwordService.requestResetCode(request, correlationId), correlationId);
    }

    /**
     * Restablece la contrasena con el codigo recibido por correo.
     *
     * @param request correo, codigo y nueva contrasena con su confirmacion
     * @param http    peticion HTTP, de donde se resuelve X-Correlation-ID
     */
    @PostMapping("/auth/password/reset")
    public ResponseEntity<MessageResponse> reset(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest http) {
        String correlationId = CorrelationId.from(http);
        return ok(passwordService.resetPassword(request, correlationId), correlationId);
    }

    /**
     * Flujo B: cambia la contrasena del usuario autenticado. La identidad llega solo por la cabecera
     * X-User-Id inyectada por el API Gateway (RN-MS01-23), nunca por el cuerpo. Si faltan X-User-Id o
     * X-User-Role responde 401 NO_AUTENTICADO; si el rol no tiene el permiso, 403 PROHIBIDO.
     *
     * @param rawUserId id del usuario autenticado (X-User-Id)
     * @param rawRoles  roles del usuario separados por coma (X-User-Role)
     * @param request contrasena actual, nueva y confirmacion
     * @param http    peticion HTTP, de donde se resuelve X-Correlation-ID
     */
    @PutMapping("/users/me/password")
    public ResponseEntity<MessageResponse> change(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestHeader(value = "X-User-Role", required = false) String rawRoles,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest http) {
        String correlationId = CorrelationId.from(http);
        UUID userId = IdentityHeaders.requireUserId(rawUserId);
        IdentityHeaders.requireAnyRole(rawRoles, CHANGE_PASSWORD_ROLES);
        return ok(passwordService.changePassword(userId, request, correlationId), correlationId);
    }

    private static ResponseEntity<MessageResponse> ok(MessageResponse body, String correlationId) {
        return ResponseEntity.ok().header(CorrelationId.HEADER, correlationId).body(body);
    }
}