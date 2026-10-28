package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de auditoría que MS01 emite a MS08 (RNF-MS01-10, Estándares de Integración, sección 12)
 *
 * Por diseño no tiene campos para contraseñas, hashes, códigos, tokens ni datos personales distintos
 * de los ids
 *
 * @param accion qué ocurrió (LOGIN, REGISTRO, ...)
 * @param resultado EXITO o FALLO
 * @param usuarioId usuario que ejecuta la acción; null si es anónimo, por ejemplo un login con un correo
 *        inexistente
 * @param usuarioRoles roles del usuario que ejecuta la acción tal como llegaron en X-User-Role, se propagan
 *        al Gateway junto con el usuario cuando la acción la hace un usuario autenticado; null si la
 *        petición era pública
 * @param detalle código genérico del motivo del fallo (p. ej. CREDENCIALES_INCORRECTAS); null cuando no
 *        aplica, y en un cambio de rol o de estado exitoso lleva lo que cambió
 * @param correlationId identificador de trazabilidad de la petición (X-Correlation-ID)
 * @param fecha instante del evento (UTC)
 * @param recursoAfectado recurso sobre el que se actuó con el formato tipo:id (p. ej. usuario:3f6c2a9e-...);
 *        null solo cuando no hay un recurso identificable
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
     * Valida que los componentes obligatorios estén presentes
     */
    public AuditEvent {
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(fecha, "fecha");
    }
}
