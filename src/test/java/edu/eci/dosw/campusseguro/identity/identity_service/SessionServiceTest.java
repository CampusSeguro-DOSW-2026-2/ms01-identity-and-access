package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionServiceImpl;

/**
 * Tests of the logout (RF-MS01-04), the central rule is that the logout is audited with the user and the date, that it
 * does not depend on the database and that it does not change the response if the audit fails
 */
class SessionServiceTest {

    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private AuditEventPublisher auditPublisher;
    private SessionServiceImpl sessionService;

        /**
     * Prepares the service with a simulated audit publisher and a fixed clock
     */
    @BeforeEach
    void setUp() {
        auditPublisher = mock(AuditEventPublisher.class);
        sessionService = serviceWith(auditPublisher);
    }

        /**
     * Builds the service with the indicated publisher and a fixed UTC clock
     *
     * @param publisher audit event publisher
     * @return the configured service
     */
    private SessionServiceImpl serviceWith(AuditEventPublisher publisher) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new SessionServiceImpl(new AuditRecorder(publisher, clock));
    }

        /**
     * Builds the identity of a requester with the indicated roles
     *
     * @param id identifier of the user
     * @param roles roles of the user
     * @return the identity of the requester
     */
    private RequesterIdentity requester(UUID id, Set<String> roles) {
        return TestIdentities.of(id, roles.toArray(new String[0]));
    }

        /**
     * Captures the only audit event emitted
     *
     * @return the emitted event
     */
    private AuditEvent singleAuditEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(captor.capture());
        return captor.getValue();
    }

        /**
     * A correct logout emits a single successful LOGOUT event with the user, the resource, the date and the correlation
     * id
     */
    @Test
    void logout_validRequester_emitsOneSuccessfulLogoutEventWithUserDateAndCorrelationId() {
        UUID id = UUID.randomUUID();

        sessionService.logout(requester(id, Set.of("ESTUDIANTE")), CORRELATION_ID);

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.LOGOUT, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(id, event.usuarioId());
        assertEquals("usuario:" + id, event.recursoAfectado());
        assertEquals(NOW, event.fecha());
        assertEquals(CORRELATION_ID, event.correlationId());
        assertNull(event.detalle());
        verifyNoMoreInteractions(auditPublisher);
    }

        /**
     * The logout works with any role of the catalog
     *
     * @param role role of the requester
     */
    @ParameterizedTest
    @MethodSource("edu.eci.dosw.campusseguro.identity.identity_service.SeedRoles#names")
    void logout_anyRole_isAcceptedAndAudited(String role) {
        UUID id = UUID.randomUUID();

        assertDoesNotThrow(() -> sessionService.logout(requester(id, Set.of(role)), CORRELATION_ID));

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(role, event.usuarioRoles());
    }

        /**
     * With several roles the event propagates the roles as they travel in X-User-Role, in alphabetical order
     */
    @Test
    void logout_severalRoles_propagatesThemInAlphabeticalOrder() {
        RequesterIdentity identity = requester(UUID.randomUUID(),
                Set.of("ESTUDIANTE", "BRIGADISTA"));

        sessionService.logout(identity, CORRELATION_ID);

        assertEquals("BRIGADISTA,ESTUDIANTE", singleAuditEvent().usuarioRoles());
    }

        /**
     * If the correlation id does not arrive a new valid one is generated for the event
     */
    @Test
    void logout_withoutCorrelationId_generatesOneForTheEvent() {
        sessionService.logout(requester(UUID.randomUUID(), Set.of("ADMIN")), null);

        String generated = singleAuditEvent().correlationId();
        assertEquals(generated, UUID.fromString(generated).toString());
        assertEquals('4', generated.charAt(14));
    }

        /**
     * A correlation id that is not a UUID v4 is replaced by a valid one
     */
    @Test
    void logout_withInvalidCorrelationId_replacesItWithAValidOne() {
        sessionService.logout(requester(UUID.randomUUID(), Set.of("ADMIN")), "no-es-un-uuid");

        String used = singleAuditEvent().correlationId();
        assertNotEquals("no-es-un-uuid", used);
        assertEquals(used, UUID.fromString(used).toString());
    }

        /**
     * If the audit fails the logout completes the same, the response to the user does not change
     */
    @Test
    void logout_whenTheAuditFails_stillCompletes() {
        AuditEventPublisher failing = mock(AuditEventPublisher.class);
        doThrow(new IllegalStateException("MS08 unavailable")).when(failing).publish(any(AuditEvent.class));
        SessionServiceImpl service = serviceWith(failing);

        assertDoesNotThrow(() -> service.logout(requester(UUID.randomUUID(), Set.of("ESTUDIANTE")),
                CORRELATION_ID));

        verify(failing).publish(any(AuditEvent.class));
    }

        /**
     * Logging out several times is valid: the token is not revoked, so each logout is audited
     */
    @Test
    void logout_calledTwice_auditsEachCall() {
        RequesterIdentity identity = requester(UUID.randomUUID(), Set.of("PROFESOR"));

        sessionService.logout(identity, CORRELATION_ID);
        sessionService.logout(identity, CORRELATION_ID);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(2)).publish(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(event -> event.resultado() == AuditResult.EXITO));
    }

        /**
     * A requester without any permission is rejected with 403 and the failure is audited with a generic reason
     */
    @Test
    void logout_withoutPermission_isRejectedAndTheFailureIsAudited() {
        UUID id = UUID.randomUUID();
        RequesterIdentity withoutPermissions = requester(id, Set.of());

        ForbiddenOperationException error = assertThrows(ForbiddenOperationException.class,
                () -> sessionService.logout(withoutPermissions, CORRELATION_ID));

        assertEquals(ErrorCode.FORBIDDEN.getMessage(), error.getMessage());
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.LOGOUT, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("SIN_PERMISO", event.detalle());
        assertEquals(id, event.usuarioId());
        assertEquals("usuario:" + id, event.recursoAfectado());
    }

        /**
     * A rejection for lack of permission emits a single event and never a successful one
     */
    @Test
    void logout_withoutPermission_neverEmitsASuccessfulEvent() {
        RequesterIdentity withoutPermissions = requester(UUID.randomUUID(), Set.of());

        assertThrows(ForbiddenOperationException.class,
                () -> sessionService.logout(withoutPermissions, CORRELATION_ID));

        assertNotEquals(AuditResult.EXITO, singleAuditEvent().resultado());
        verifyNoMoreInteractions(auditPublisher);
    }

        /**
     * The event contains no tokens, passwords or personal data: only ids, roles and codes
     */
    @Test
    void logout_event_containsNoSecretsOrPersonalData() {
        UUID id = UUID.randomUUID();

        sessionService.logout(requester(id, Set.of("ESTUDIANTE")), CORRELATION_ID);

        AuditEvent event = singleAuditEvent();
        String content = event.toString().toLowerCase();
        assertTrue(content.contains(id.toString()));
        assertFalse(content.contains("token"));
        assertFalse(content.contains("password"));
        assertFalse(content.contains("contrasena"));
        assertFalse(content.contains("@"));
    }
}
