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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.eci.dosw.campusseguro.identity.identity_service.TestIdentities;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Construction and emission of the audit events towards MS08 (RNF-MS01-05, Integration Standards, section 7)
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
     * Prepares the recorder with a fixed clock
     */
    @BeforeEach
    void setUp() {
        recorder = new AuditRecorder(publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

        /**
     * Gets the event that was delivered to the publisher
     *
     * @return the emitted event
     */
    private AuditEvent publishedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

        /**
     * An anonymous event carries no roles and uses the time of the clock
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
     * An anonymous event with a user keeps the user and the resource
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
     * An event of a requester carries their identifier and their roles in the alphabetical order of the header
     */
    @Test
    void recordAs_publishesTheRequesterAndItsRolesInHeaderOrder() {
        RequesterIdentity requester = TestIdentities.of(USER_ID, "PROFESOR", "ADMIN");

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
     * The resource of a user uses the type and the identifier separated by a colon
     */
    @Test
    void userResource_usesTheTypeAndTheIdentifier() {
        assertEquals("usuario:" + USER_ID, AuditRecorder.userResource(USER_ID));
    }

        /**
     * Without an identifier there is no affected resource
     */
    @Test
    void userResource_withoutIdentifier_isNull() {
        assertNull(AuditRecorder.userResource(null));
    }

        /**
     * A failure of the publisher never interrupts the audited operation
     */
    @Test
    void publisherFailure_isSwallowed() {
        doThrow(new IllegalStateException("MS08 unavailable")).when(publisher).publish(any(AuditEvent.class));

        assertDoesNotThrow(() -> recorder.recordAnonymous(AuditAction.LOGIN, AuditResult.EXITO, USER_ID,
                AuditRecorder.userResource(USER_ID), null, CORRELATION_ID));
    }
}
