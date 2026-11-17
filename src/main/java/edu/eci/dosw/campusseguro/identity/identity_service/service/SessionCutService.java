package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.SessionCutsResponse;

/**
 * Query of the current session cuts (RF-MS01-11), the API Gateway uses it to reject the credentials issued before the
 * cut of a user or of any of their roles
 */
public interface SessionCutService {

        /**
     * Builds the snapshot of the session cuts of the users and roles within the validity window
     *
     * @return the snapshot with the instant at which it was built, the window and the cuts sorted
     */
    SessionCutsResponse currentCuts();
}
