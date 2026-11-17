package edu.eci.dosw.campusseguro.identity.identity_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * RF-MS01-04 Log out (logout)
 *
 * Base flow: identify the authenticated user (X-User-Id) -> audit LOGOUT/EXITO -> respond
 *
 * RN-MS01-10: The logout is audited and does not revoke the token already issued, which remains valid until it expires,
 * the client discards it
 *
 * RNF-MS01-10: The successful logout and the one rejected for lack of permission are audited, a failure when auditing
 * never changes the response
 */
@Service
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    static final String REASON_NOT_ALLOWED = "SIN_PERMISO";

    private final AuditRecorder auditRecorder;

        /**
     * Creates the service with its dependency
     *
     * @param auditRecorder recorder of the audit events of MS08
     */
    public SessionServiceImpl(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

        /**
     * Closes the session of the requester and records the audit event (RF-MS01-04), the logout does not revoke the
     * token already issued, which remains valid until it expires (RN-MS01-10)
     *
     * @param requester identity of whoever closes the session
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @throws ForbiddenOperationException if the requester does not have the required permission
     */
    @Override
    public void logout(RequesterIdentity requester, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);
        String resource = AuditRecorder.userResource(requester.userId());

        if (!requester.hasPermission(Permission.MS01_SESSION_LOGOUT)) {
            auditRecorder.recordAs(AuditAction.LOGOUT, AuditResult.FALLO, requester, resource,
                    REASON_NOT_ALLOWED, corr);
            log.warn("Logout rejected: the requester {} does not have the required permission",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }

        auditRecorder.recordAs(AuditAction.LOGOUT, AuditResult.EXITO, requester, resource, null, corr);
        log.info("Logout. user={}", requester.userId());
    }
}
