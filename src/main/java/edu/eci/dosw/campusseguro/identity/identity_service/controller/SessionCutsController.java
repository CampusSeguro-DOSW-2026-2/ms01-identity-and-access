package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.SessionCutsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionCutService;

/**
 * Consulta interna de los cortes de sesión vigentes (RF-MS01-11), solo la atiende el servicio gateway
 */
@RestController
@RequestMapping("/api/v1/internal")
public class SessionCutsController {

    private final SessionCutService sessionCutService;

    /**
     * Crea el controlador con su servicio
     *
     * @param sessionCutService servicio de consulta de cortes de sesión
     */
    public SessionCutsController(SessionCutService sessionCutService) {
        this.sessionCutService = sessionCutService;
    }

    /**
     * GET /api/v1/internal/session-cuts: instantánea de los cortes de sesión (RF-MS01-11)
     *
     * 200 cortes vigentes, 401 falta X-Service-Name, 403 el servicio no es gateway, no se audita
     *
     * @return la instantánea de cortes de usuarios y roles de los últimos 3700 segundos
     */
    @GetMapping("/session-cuts")
    public ResponseEntity<SessionCutsResponse> getSessionCuts() {
        return ResponseEntity.ok(sessionCutService.currentCuts());
    }
}
