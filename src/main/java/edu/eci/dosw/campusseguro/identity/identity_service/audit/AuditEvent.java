package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de auditoria que MS01 emite a MS08 (RNF-MS01-10)
 *
 * Por diseno no tiene campos para contrasenas, hashes, codigos ni tokens
 *
 * @param accion que ocurrio (LOGIN, REGISTRO, ...)
 * @param resultado EXITO o FALLO
 * @param usuarioId id del usuario involucrado; null si el intento no corresponde a ninguna cuenta
 * @param correo correo con el que se intento la accion
 * @param detalle motivo breve y generico del fallo (p. ej. CREDENCIALES_INCORRECTAS); null en exito,
 *        salvo en CAMBIO_ROL, donde un exito lleva los roles rol_anterior=...;rol_nuevo=...
 * @param correlationId identificador de trazabilidad de la peticion (X-Correlation-ID)
 * @param fecha instante del evento (UTC)
 * @param recursoAfectado recurso sobre el que se actuo, p. ej. users/{id} (RNF-MS01-10, estandar de
 *        auditoria); null cuando la accion no recae sobre un recurso
 */
public record AuditEvent(
        AuditAction accion,
        AuditResult resultado,
        UUID usuarioId,
        String correo,
        String detalle,
        String correlationId,
        Instant fecha,
        String recursoAfectado) {

    public AuditEvent {
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(fecha, "fecha");
    }

    /**
     * Crea un evento que no recae sobre un recurso distinto del propio usuario (login, registro...),
     * es decir, con recursoAfectado en null.
     *
     * @param accion que ocurrio
     * @param resultado EXITO o FALLO
     * @param usuarioId id del usuario involucrado, es null si no hay uno involucrado
     * @param correo correo del usuario
     * @param detalle motivo breve y generico del fallo
     * @param correlationId identificador de trazabilidad de la peticion
     * @param fecha instante del evento (UTC)
     */
    public AuditEvent(AuditAction accion, AuditResult resultado, UUID usuarioId, String correo,
            String detalle, String correlationId, Instant fecha) {
        this(accion, resultado, usuarioId, correo, detalle, correlationId, fecha, null);
    }
}
