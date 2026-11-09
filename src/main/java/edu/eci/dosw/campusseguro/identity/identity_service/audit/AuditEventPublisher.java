package edu.eci.dosw.campusseguro.identity.identity_service.audit;

/**
 * Puerto de salida hacia la auditoría centralizada (MS08)
 *
 * Contrato: la publicación es asíncrona y NUNCA lanza excepciones ni retrasa la respuesta al cliente
 * Si MS08 falla, el error se registra en logs y el flujo de negocio continúa (RNF-MS01-10)
 */
public interface AuditEventPublisher {

    /**
     * Publica un evento de auditoría sin esperar el resultado
     *
     * @param event evento que se emite
     */
    void publish(AuditEvent event);
}
