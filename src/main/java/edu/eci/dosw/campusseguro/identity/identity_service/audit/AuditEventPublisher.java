package edu.eci.dosw.campusseguro.identity.identity_service.audit;

/**
 * Puerto de salida hacia la auditoria centralizada (MS08).
 *
 * Contrato: la publicacion es asincrona y NUNCA lanza excepciones ni retrasa la respuesta al cliente.
 * Si MS08 falla, el error se registra en logs y el flujo de negocio continua (RNF-MS01-10).
 */
public interface AuditEventPublisher {

    void publish(AuditEvent event);
}
