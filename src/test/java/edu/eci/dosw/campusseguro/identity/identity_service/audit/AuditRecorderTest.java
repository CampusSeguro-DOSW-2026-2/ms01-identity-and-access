package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Construcción y emisión de los eventos de auditoría hacia MS08 (RNF-MS01-05, Estándares de Integración,
 * sección 7)
 */
@ExtendWith(MockitoExtension.class)
class AuditRecorderTest {

    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00.789Z");
    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    @Mock
    private AuditEventPublisher publisher;

    private AuditRecorder recorder;

    /**
     * Prepara el registrador con un reloj fijo
     */
    @BeforeEach
    void setUp() {
        recorder = new AuditRecorder(publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * Obtiene el evento que se entregó al publicador
     *
     * @return el evento emitido
     */
    private AuditEvent publishedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    /**
     * Un evento anónimo no lleva roles y usa la hora del reloj
     */
    @Test
    void recordAnonymous_publishesTheEventWithoutRoles() {
        recorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.FALLO, null, null, "VALIDACION",
                CORRELATION_ID);

        AuditEvent event = publishedEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertNull(event.usuarioId());
        assertNull(event.usuarioRoles());
        assertNull(event.recursoAfectado());
        assertEquals("VALIDACION", event.detalle());
        assertEquals(CORRELATION_ID, event.correlationId());
        assertEquals(NOW, event.fecha());
    }

    /**
     * Un evento anónimo con usuario conserva el usuario y el recurso
     */
    @Test
    void recordAnonymous_withUser_keepsTheUserAndTheResource() {
        recorder.recordAnonymous(AuditAction.LOGIN, AuditResult.EXITO, USER_ID,
                AuditRecorder.userResource(USER_ID), null, CORRELATION_ID);

        AuditEvent event = publishedEvent();
        assertEquals(USER_ID, event.usuarioId());
        assertEquals("usuario:" + USER_ID, event.recursoAfectado());
        assertNull(event.detalle());
    }

    /**
     * Un evento de un solicitante lleva su identificador y sus roles en el orden alfabético de la cabecera
     */
    @Test
    void recordAs_publishesTheRequesterAndItsRolesInHeaderOrder() {
        RequesterIdentity requester = new RequesterIdentity(USER_ID, Set.of(UserRole.PROFESOR, UserRole.ADMIN));

        recorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.EXITO, requester, "usuario:otro", "ACTIVO",
                CORRELATION_ID);

        AuditEvent event = publishedEvent();
        assertEquals(USER_ID, event.usuarioId());
        assertEquals("ADMIN,PROFESOR", event.usuarioRoles());
        assertEquals("usuario:otro", event.recursoAfectado());
        assertEquals("ACTIVO", event.detalle());
        assertEquals(NOW, event.fecha());
    }

    /**
     * El recurso de un usuario usa el tipo y el identificador separados por dos puntos
     */
    @Test
    void userResource_usesTheTypeAndTheIdentifier() {
        assertEquals("usuario:" + USER_ID, AuditRecorder.userResource(USER_ID));
    }

    /**
     * Sin identificador no hay recurso afectado
     */
    @Test
    void userResource_withoutIdentifier_isNull() {
        assertNull(AuditRecorder.userResource(null));
    }

    /**
     * Un fallo del publicador nunca interrumpe la operación auditada
     */
    @Test
    void publisherFailure_isSwallowed() {
        doThrow(new IllegalStateException("MS08 no disponible")).when(publisher).publish(any(AuditEvent.class));

        assertDoesNotThrow(() -> recorder.recordAnonymous(AuditAction.LOGIN, AuditResult.EXITO, USER_ID,
                AuditRecorder.userResource(USER_ID), null, CORRELATION_ID));
    }
}
