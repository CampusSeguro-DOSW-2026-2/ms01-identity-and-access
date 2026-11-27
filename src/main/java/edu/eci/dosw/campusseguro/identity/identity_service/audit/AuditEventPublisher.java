package edu.eci.dosw.campusseguro.identity.identity_service.audit;

/**
 * Output port towards the centralized audit (MS08)
 *
 * Contract: the publication is asynchronous and NEVER throws exceptions or delays the response to the client If MS08
 * fails, the error is logged and the business flow continues (RNF-MS01-10)
 */
public interface AuditEventPublisher {

        /**
     * Publishes an audit event without waiting for the result
     *
     * @param event event that is emitted
     */
    void publish(AuditEvent event);
}
