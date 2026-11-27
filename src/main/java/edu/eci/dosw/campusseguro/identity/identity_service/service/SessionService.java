package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Logout of the authenticated user (RF-MS01-04)
 */
public interface SessionService {

        /**
     * Closes the session of the requester and records the audit event (RF-MS01-04), the logout does not revoke the
     * token already issued, which remains valid until it expires (RN-MS01-10)
     *
     * @param requester identity of whoever closes the session
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @throws ForbiddenOperationException if the requester does not have the required permission
     */
    void logout(RequesterIdentity requester, String correlationId);
}
