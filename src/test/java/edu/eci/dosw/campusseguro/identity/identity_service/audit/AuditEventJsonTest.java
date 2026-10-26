package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Cuerpo JSON del evento de auditoría (RNF-MS01-10, Estándares de Integración): modulo MS01, recurso
 * afectado con el formato tipo:id, fecha en ISO UTC y sin datos personales
 *
 * Esta prueba está en el mismo paquete que AuditEventPublisherImpl porque toJson es privado del paquete
 */
class AuditEventJsonTest {

    private static final UUID ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    /**
     * El evento CAMBIO_ROL exitoso incluye quién lo hizo, a quién y lo que cambió
     */
    @Test
    void toJson_roleChangeSuccess_includesTheAffectedResourceAndTheChanges() {
        AuditEvent event = new AuditEvent(AuditAction.CAMBIO_ROL, AuditResult.EXITO, ADMIN_ID, "ADMIN",
                "agregados=PROFESOR;retirados=", CORRELATION_ID, NOW, "usuario:" + TARGET_ID);

        assertEquals("{\"usuario_id\":\"" + ADMIN_ID + "\",\"modulo\":\"MS01\",\"accion\":\"CAMBIO_ROL\","
                + "\"recurso_afectado\":\"usuario:" + TARGET_ID + "\",\"resultado\":\"EXITO\","
                + "\"fecha_hora\":\"2026-10-23T15:00:00Z\",\"correlation_id\":\"" + CORRELATION_ID + "\","
                + "\"detalle\":\"agregados=PROFESOR;retirados=\"}", AuditEventPublisherImpl.toJson(event));
    }

    /**
     * Los roles del actor viajan solo como cabecera, nunca en el cuerpo
     */
    @Test
    void toJson_neverIncludesTheActorRolesInTheBody() {
        AuditEvent event = new AuditEvent(AuditAction.CAMBIO_ESTADO, AuditResult.EXITO, ADMIN_ID, "ADMIN", null,
                CORRELATION_ID, NOW, "usuario:" + TARGET_ID);

        String json = AuditEventPublisherImpl.toJson(event);

        assertTrue(!json.contains("usuario_roles"));
        assertTrue(!json.contains("ADMIN"));
    }

    /**
     * Un evento anónimo sin recurso envía las claves opcionales con valor null
     */
    @Test
    void toJson_anonymousEvent_sendsExplicitNulls() {
        AuditEvent event = new AuditEvent(AuditAction.LOGIN, AuditResult.FALLO, null, null,
                "CREDENCIALES_INCORRECTAS", CORRELATION_ID, NOW, null);

        assertEquals("{\"usuario_id\":null,\"modulo\":\"MS01\",\"accion\":\"LOGIN\","
                + "\"recurso_afectado\":null,\"resultado\":\"FALLO\","
                + "\"fecha_hora\":\"2026-10-23T15:00:00Z\",\"correlation_id\":\"" + CORRELATION_ID + "\","
                + "\"detalle\":\"CREDENCIALES_INCORRECTAS\"}", AuditEventPublisherImpl.toJson(event));
    }

    /**
     * Un rechazo del cambio de roles lleva el motivo en detalle y el usuario afectado en recurso_afectado
     */
    @Test
    void toJson_roleChangeFailure_includesTheReason() {
        AuditEvent event = new AuditEvent(AuditAction.CAMBIO_ROL, AuditResult.FALLO, ADMIN_ID, "ADMIN",
                "AUTOMODIFICACION_DE_ROLES", CORRELATION_ID, NOW, "usuario:" + ADMIN_ID);

        String json = AuditEventPublisherImpl.toJson(event);

        assertTrue(json.contains("\"resultado\":\"FALLO\""));
        assertTrue(json.contains("\"detalle\":\"AUTOMODIFICACION_DE_ROLES\""));
        assertTrue(json.contains("\"recurso_afectado\":\"usuario:" + ADMIN_ID + "\""));
    }

    /**
     * Los campos obligatorios siguen siendo obligatorios en el constructor
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
