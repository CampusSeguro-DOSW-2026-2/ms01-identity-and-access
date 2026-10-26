package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de auditoria que MS01 emite a MS08 (RNF-MS01-10, Estandares seccion 12).
 *
 * Por diseno no tiene campos para contrasenas, hashes, codigos ni tokens.
 *
 * @param accion que ocurrio (LOGIN, REGISTRO, ...)
 * @param resultado EXITO o FALLO
 * @param usuarioId id del usuario involucrado; null si el intento no corresponde a ninguna cuenta
 * @param correo correo con el que se intento la accion; solo para uso interno (logs), no se envia a MS08
 * @param detalle motivo breve y generico del fallo (p. ej. CREDENCIALES_INCORRECTAS); null en exito
 * @param correlationId identificador de trazabilidad de la peticion (X-Correlation-ID)
 * @param fecha instante del evento (UTC)
 * @param recursoAfectado recurso sobre el que se actuo, con formato {@code <tipo>:<id>} (p. ej.
 *        {@code usuario:3f6c2a9e-...}); si no se indica y hay usuario, se deriva de {@code usuarioId}
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
        if (recursoAfectado == null && usuarioId != null) {
            recursoAfectado = "usuario:" + usuarioId;
        }
    }

    /** Constructor sin recurso explicito: el recurso se deriva del usuario cuando existe. */
    public AuditEvent(AuditAction accion, AuditResult resultado, UUID usuarioId, String correo, String detalle,
            String correlationId, Instant fecha) {
        this(accion, resultado, usuarioId, correo, detalle, correlationId, fecha, null);
    }
}