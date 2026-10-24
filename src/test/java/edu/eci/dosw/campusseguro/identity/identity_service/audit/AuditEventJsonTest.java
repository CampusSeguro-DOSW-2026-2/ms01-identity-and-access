package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Evento de auditoria CAMBIO_ROL (RF-MS01-05, RNF-MS01-10): el evento lleva el recurso afectado (a quien
 * se le cambiaron los roles) y los roles anterior y nuevo, y los eventos que no lo usan (login) siguen
 * serializandose igual que antes.
 *
 * Esta prueba esta en el mismo paquete que Ms08AuditEventPublisher porque toJson es privado del paquete.
 */
class AuditEventJsonTest {

    private static final UUID ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");

    /**
     * El evento CAMBIO_ROL exitoso incluye quien lo hizo, a quien, y los roles anterior y nuevo.
     */
    @Test
    void toJson_roleChangeSuccess_includesTheAffectedResourceAndTheRoles() {
        AuditEvent event = new AuditEvent(AuditAction.CAMBIO_ROL, AuditResult.EXITO, ADMIN_ID, null,
                "rol_anterior=ESTUDIANTE;rol_nuevo=ESTUDIANTE,PROFESOR", "corr-1", NOW, "users/" + TARGET_ID);

        assertEquals("{\"origen\":\"ms01-identity-and-access\",\"accion\":\"CAMBIO_ROL\",\"resultado\":\"EXITO\","
                + "\"usuario_id\":\"" + ADMIN_ID + "\","
                + "\"detalle\":\"rol_anterior=ESTUDIANTE;rol_nuevo=ESTUDIANTE,PROFESOR\","
                + "\"recurso_afectado\":\"users/" + TARGET_ID + "\","
                + "\"correlation_id\":\"corr-1\",\"fecha\":\"2026-10-23T15:00:00Z\"}",
                Ms08AuditEventPublisher.toJson(event));
    }

    /**
     * Un evento sin recurso afectado no incluye la clave recurso_afectado (los eventos de login no
     * cambian).
     */
    @Test
    void toJson_withoutAffectedResource_omitsTheKey() {
        AuditEvent event = new AuditEvent(AuditAction.LOGIN, AuditResult.EXITO, ADMIN_ID, "ana@escuelaing.edu.co",
                null, "corr-2", NOW);

        String json = Ms08AuditEventPublisher.toJson(event);

        assertFalse(json.contains("recurso_afectado"));
        assertEquals("{\"origen\":\"ms01-identity-and-access\",\"accion\":\"LOGIN\",\"resultado\":\"EXITO\","
                + "\"usuario_id\":\"" + ADMIN_ID + "\",\"correo\":\"ana@escuelaing.edu.co\","
                + "\"correlation_id\":\"corr-2\",\"fecha\":\"2026-10-23T15:00:00Z\"}", json);
    }

    /**
     * Un rechazo del cambio de roles lleva el motivo en detalle y el usuario afectado en recurso_afectado.
     */
    @Test
    void toJson_roleChangeFailure_includesTheReason() {
        AuditEvent event = new AuditEvent(AuditAction.CAMBIO_ROL, AuditResult.FALLO, ADMIN_ID, null,
                "AUTOMODIFICACION_DE_ROLES", null, NOW, "users/" + ADMIN_ID);

        String json = Ms08AuditEventPublisher.toJson(event);

        assertTrue(json.contains("\"resultado\":\"FALLO\""));
        assertTrue(json.contains("\"detalle\":\"AUTOMODIFICACION_DE_ROLES\""));
        assertTrue(json.contains("\"recurso_afectado\":\"users/" + ADMIN_ID + "\""));
        assertFalse(json.contains("correlation_id"));
    }

    /**
     * El constructor de siete argumentos que usa el login deja recursoAfectado en null.
     */
    @Test
    void constructor_withoutResource_leavesItNull() {
        AuditEvent event = new AuditEvent(AuditAction.LOGIN, AuditResult.FALLO, null, "x@escuelaing.edu.co",
                "CREDENCIALES_INCORRECTAS", "corr-3", NOW);

        assertNull(event.recursoAfectado());
        assertEquals("CREDENCIALES_INCORRECTAS", event.detalle());
    }

    /**
     * Los campos obligatorios siguen siendo obligatorios en el constructor completo.
     */
    @Test
    void constructor_requiresActionResultAndDate() {
        assertThrows(NullPointerException.class, () -> new AuditEvent(null, AuditResult.EXITO, null, null,
                null, null, NOW, null));
        assertThrows(NullPointerException.class, () -> new AuditEvent(AuditAction.CAMBIO_ROL, null, null, null,
                null, null, NOW, null));
        assertThrows(NullPointerException.class, () -> new AuditEvent(AuditAction.CAMBIO_ROL, AuditResult.EXITO,
                null, null, null, null, null, null));
    }
}
