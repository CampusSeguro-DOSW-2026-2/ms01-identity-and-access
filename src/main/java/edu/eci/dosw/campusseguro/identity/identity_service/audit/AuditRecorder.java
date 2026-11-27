package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Records the audit events of MS01 (RNF-MS01-10) with the format of the Integration Standards, centralizes how the user
 * and the resource are identified, and guarantees that a failure when auditing never alters the response to the client
 */
@Component
public class AuditRecorder {

    public static final String USER_RESOURCE_TYPE = "usuario";
    public static final String ROLE_RESOURCE_TYPE = "rol";

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditEventPublisher publisher;
    private final Clock clock;

        /**
     * Creates the recorder with the output port and the clock
     *
     * @param publisher output port towards MS08
     * @param clock UTC clock with which each event is dated
     */
    public AuditRecorder(AuditEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

        /**
     * Records an event of an action that is not executed by an authenticated user, for example the registration or the
     * login, no identity is propagated to the Gateway
     *
     * @param action audited action
     * @param result EXITO or FALLO
     * @param userId user involved, null if it does not correspond to any account
     * @param resource affected resource with the format type:id, null if there is no identifiable one
     * @param detail generic code of the reason, null if it does not apply
     * @param correlationId correlation id of the request
     */
    public void recordAnonymous(AuditAction action, AuditResult result, UUID userId, String resource,
            String detail, String correlationId) {
        publish(new AuditEvent(action, result, userId, null, detail, correlationId, clock.instant(), resource));
    }

        /**
     * Records an event of an action executed by an authenticated user, its identity is propagated to the Gateway as it
     * arrived in the headers
     *
     * @param action audited action
     * @param result EXITO or FALLO
     * @param requester identity of whoever executes the action
     * @param resource affected resource with the format type:id, null if there is no identifiable one
     * @param detail generic code of the reason or what changed, null if it does not apply
     * @param correlationId correlation id of the request
     */
    public void recordAs(AuditAction action, AuditResult result, RequesterIdentity requester, String resource,
            String detail, String correlationId) {
        publish(new AuditEvent(action, result, requester.userId(), requester.rolesHeader(), detail,
                correlationId, clock.instant(), resource));
    }

        /**
     * Builds the affected resource of a user with the format type:id
     *
     * @param userId id of the affected user, may be null
     * @return the resource with the format usuario:id, or null if the user is not known
     */
    public static String userResource(UUID userId) {
        if (userId == null) {
            return null;
        }
        return USER_RESOURCE_TYPE + ":" + userId;
    }

        /**
     * Builds the affected resource of a role with the format type:id
     *
     * @param roleName name of the affected role, may be null
     * @return the resource with the format rol:NAME, or null if the role is not known
     */
    public static String roleResource(String roleName) {
        if (roleName == null) {
            return null;
        }
        return ROLE_RESOURCE_TYPE + ":" + roleName;
    }

        /**
     * Hands the event to the output port, a failure is logged and discarded
     *
     * @param event event that is emitted
     */
    private void publish(AuditEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException e) {
            log.error("Emitting the audit event {}/{} failed: {}", event.accion(), event.resultado(),
                    e.toString());
        }
    }
}
