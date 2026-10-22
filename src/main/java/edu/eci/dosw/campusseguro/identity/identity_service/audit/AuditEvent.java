package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de auditoria que MS01 emite a MS08 (RNF-MS01-10).
 *
 * Por diseno no tiene campos para contrasenas, hashes, codigos ni tokens: no hay forma de
 * adjuntarlos por accidente.
 *
 * @param accion        que ocurrio (LOGIN, REGISTRO, ...)
 * @param resultado     EXITO o FALLO
 * @param usuarioId     usuario involucrado; null si el intento no corresponde a ninguna cuenta
 * @param correo        correo con el que se intento la accion
 * @param detalle       motivo breve y generico del fallo (p. ej. CREDENCIALES_INCORRECTAS); null en exito
 * @param correlationId identificador de trazabilidad de la peticion (X-Correlation-ID)
 * @param fecha         instante del evento (UTC)
 */
public record AuditEvent(
        AuditAction accion,
        AuditResult resultado,
        UUID usuarioId,
        String correo,
        String detalle,
        String correlationId,
        Instant fecha) {

    public AuditEvent {
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(fecha, "fecha");
    }
}
