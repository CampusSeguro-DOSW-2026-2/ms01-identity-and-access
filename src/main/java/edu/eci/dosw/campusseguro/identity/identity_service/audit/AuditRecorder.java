package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Registra los eventos de auditoría de MS01 (RNF-MS01-10) con el formato de los Estándares de
 * Integración, centraliza cómo se identifica al usuario y el recurso, y garantiza que un fallo al auditar
 * nunca altere la respuesta al cliente
 */
@Component
public class AuditRecorder {

    public static final String USER_RESOURCE_TYPE = "usuario";

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditEventPublisher publisher;
    private final Clock clock;

    /**
     * Crea el registrador con el puerto de salida y el reloj
     *
     * @param publisher puerto de salida hacia MS08
     * @param clock reloj UTC con el que se fecha cada evento
     */
    public AuditRecorder(AuditEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Registra un evento de una acción que no la ejecuta un usuario autenticado, por ejemplo el registro o
     * el login, no se propaga ninguna identidad al Gateway
     *
     * @param action acción auditada
     * @param result EXITO o FALLO
     * @param userId usuario involucrado, null si no corresponde a ninguna cuenta
     * @param resource recurso afectado con el formato tipo:id, null si no hay uno identificable
     * @param detail código genérico del motivo, null si no aplica
     * @param correlationId correlation id de la petición
     */
    public void recordAnonymous(AuditAction action, AuditResult result, UUID userId, String resource,
            String detail, String correlationId) {
        publish(new AuditEvent(action, result, userId, null, detail, correlationId, clock.instant(), resource));
    }

    /**
     * Registra un evento de una acción que ejecuta un usuario autenticado, su identidad se propaga al
     * Gateway tal como llegó en las cabeceras
     *
     * @param action acción auditada
     * @param result EXITO o FALLO
     * @param requester identidad de quien ejecuta la acción
     * @param resource recurso afectado con el formato tipo:id, null si no hay uno identificable
     * @param detail código genérico del motivo o lo que cambió, null si no aplica
     * @param correlationId correlation id de la petición
     */
    public void recordAs(AuditAction action, AuditResult result, RequesterIdentity requester, String resource,
            String detail, String correlationId) {
        publish(new AuditEvent(action, result, requester.userId(), requester.rolesHeader(), detail,
                correlationId, clock.instant(), resource));
    }

    /**
     * Construye el recurso afectado de un usuario con el formato tipo:id
     *
     * @param userId id del usuario afectado, puede ser null
     * @return el recurso con el formato usuario:id, o null si no se conoce el usuario
     */
    public static String userResource(UUID userId) {
        if (userId == null) {
            return null;
        }
        return USER_RESOURCE_TYPE + ":" + userId;
    }

    /**
     * Entrega el evento al puerto de salida, un fallo se registra en el log y se descarta
     *
     * @param event evento que se emite
     */
    private void publish(AuditEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException e) {
            log.error("Falló la emisión del evento de auditoría {}/{}: {}", event.accion(), event.resultado(),
                    e.toString());
        }
    }
}
