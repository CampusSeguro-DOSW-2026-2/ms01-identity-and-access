package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.SessionCutsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionCutService;

/**
 * Internal query of the current session cuts (RF-MS01-11), only the gateway service is served
 */
@RestController
@RequestMapping("/api/v1/internal")
public class SessionCutsController {

    private final SessionCutService sessionCutService;

        /**
     * Creates the controller with its service
     *
     * @param sessionCutService session cut query service
     */
    public SessionCutsController(SessionCutService sessionCutService) {
        this.sessionCutService = sessionCutService;
    }

        /**
     * GET /api/v1/internal/session-cuts: snapshot of the session cuts (RF-MS01-11)
     *
     * 200 current cuts, 401 missing X-Service-Name, 403 the service is not gateway, it is not audited
     *
     * @return the snapshot of cuts of users and roles of the last 3700 seconds
     */
    @GetMapping("/session-cuts")
    public ResponseEntity<SessionCutsResponse> getSessionCuts() {
        return ResponseEntity.ok(sessionCutService.currentCuts());
    }
}
