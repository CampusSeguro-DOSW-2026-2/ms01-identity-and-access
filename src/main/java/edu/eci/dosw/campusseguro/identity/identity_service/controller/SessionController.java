package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Cierre de sesión (RF-MS01-04), a diferencia del registro y el login no es público: la identidad del
 * solicitante llega en las cabeceras que inyecta el API Gateway y nunca en el cuerpo de la petición
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {

    public static final String LOGOUT_MESSAGE = "Sesión cerrada.";

    private final SessionService sessionService;

    /**
     * Crea el controlador con su servicio
     *
     * @param sessionService servicio de cierre de sesión
     */
    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * POST /api/v1/auth/logout: cierra la sesión del usuario autenticado (RF-MS01-04)
     *
     * 200 sesión cerrada y registrada en la auditoría, 401 identidad ausente, 403 sin permiso
     *
     * No recibe cuerpo y no revoca el token: el cliente lo descarta y el token expira por sí solo (RN-MS01-10)
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el mensaje de confirmación
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(RequesterIdentity requester, HttpServletRequest httpRequest) {
        sessionService.logout(requester, CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(new MessageResponse(LOGOUT_MESSAGE));
    }
}
