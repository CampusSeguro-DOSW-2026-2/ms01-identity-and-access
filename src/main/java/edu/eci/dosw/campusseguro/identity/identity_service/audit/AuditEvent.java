package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit event that MS01 emits to MS08 (RNF-MS01-10, Integration Standards, section 12)
 *
 * By design it has no fields for passwords, hashes, codes, tokens or personal data other than the ids
 *
 * @param accion what happened (LOGIN, REGISTRO, ...)
 * @param resultado EXITO or FALLO
 * @param usuarioId user that executes the action; null if anonymous, for example a login with a non-existent email
 * @param usuarioRoles roles of the user that executes the action as they arrived in X-User-Role, they are propagated to
 *        the Gateway together with the user when the action is performed by an authenticated user; null if the request
 *        was public
 * @param detalle generic code of the reason for the failure (e.g. CREDENCIALES_INCORRECTAS); null when it does not
 *        apply, and in a successful role or status change it carries what changed
 * @param correlationId traceability identifier of the request (X-Correlation-ID)
 * @param fecha instant of the event (UTC)
 * @param recursoAfectado resource on which the action was taken with the format type:id (e.g. usuario:3f6c2a9e-...);
 *        null only when there is no identifiable resource
 */
public record AuditEvent(
        AuditAction accion,
        AuditResult resultado,
        UUID usuarioId,
        String usuarioRoles,
        String detalle,
        String correlationId,
        Instant fecha,
        String recursoAfectado) {

        /**
     * Validates that the required components are present
     */
    public AuditEvent {
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(fecha, "fecha");
    }
}
