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
 * Recuperación y cambio de contraseña (RF-MS01-08), la solicitud y el restablecimiento con código son
 * públicos porque se usan sin sesión, el cambio de contraseña exige la identidad que inyecta el API Gateway
 * en las cabeceras
 */
@RestController
@RequestMapping("/api/v1")
public class PasswordController {

    public static final String FORGOT_MESSAGE = "Si el correo está registrado, enviamos un código de recuperación.";
    public static final String RESET_MESSAGE = "Contraseña actualizada correctamente. Inicia sesión de nuevo.";
    public static final String CHANGE_MESSAGE = "Contraseña actualizada correctamente.";

    private final PasswordService passwordService;

    /**
     * Crea el controlador con su servicio
     *
     * @param passwordService servicio de recuperación y cambio de contraseña
     */
    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    /**
     * POST /api/v1/auth/password/forgot: solicita el código de recuperación de contraseña (RF-MS01-08,
     * formulario F-04)
     *
     * 200 siempre con el mismo mensaje, exista o no el correo (RN-MS01-20), 400 correo con formato inválido
     *
     * @param request correo de la cuenta que se quiere recuperar
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el mensaje único de confirmación
     */
    @PostMapping("/auth/password/forgot")
    public ResponseEntity<MessageResponse> forgot(@RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordService.requestRecoveryCode(request, CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(new MessageResponse(FORGOT_MESSAGE));
    }

    /**
     * POST /api/v1/auth/password/reset: restablece la contraseña con el código recibido por correo
     * (RF-MS01-08, formulario F-05)
     *
     * 200 contraseña actualizada, 400 datos inválidos o código incorrecto, vencido o ya usado con mensaje
     * genérico
     *
     * @param request correo, código y nueva contraseña con su confirmación
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el mensaje de confirmación
     */
    @PostMapping("/auth/password/reset")
    public ResponseEntity<MessageResponse> reset(@RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordService.resetPassword(request, CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(new MessageResponse(RESET_MESSAGE));
    }

    /**
     * PUT /api/v1/users/me/password: cambia la contraseña del usuario autenticado (RF-MS01-08, formulario
     * F-06)
     *
     * 200 contraseña actualizada, 400 datos inválidos, contraseña actual incorrecta o nueva igual a la
     * actual, 401 identidad ausente, 403 sin permiso, 404 usuario inexistente
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param request contraseña actual, nueva contraseña y su confirmación
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el mensaje de confirmación
     */
    @PutMapping("/users/me/password")
    public ResponseEntity<MessageResponse> change(RequesterIdentity requester,
            @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        passwordService.changePassword(requester, request, CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(new MessageResponse(CHANGE_MESSAGE));
    }
}
