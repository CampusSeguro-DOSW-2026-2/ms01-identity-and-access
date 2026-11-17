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
 * Logout (RF-MS01-04), unlike the registration and the login it is not public: the identity of the requester arrives in
 * the headers injected by the API Gateway and never in the body of the request
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {

    public static final String LOGOUT_MESSAGE = "Sesión cerrada.";

    private final SessionService sessionService;

        /**
     * Creates the controller with its service
     *
     * @param sessionService logout service
     */
    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

        /**
     * POST /api/v1/auth/logout: closes the session of the authenticated user (RF-MS01-04)
     *
     * 200 session closed and recorded in the audit, 401 missing identity, 403 no permission
     *
     * It receives no body and does not revoke the token: the client discards it and the token expires on its own
     * (RN-MS01-10)
     *
     * @param requester identity of the requester taken from the headers
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the confirmation message
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(RequesterIdentity requester, HttpServletRequest httpRequest) {
        sessionService.logout(requester, CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(new MessageResponse(LOGOUT_MESSAGE));
    }
}
