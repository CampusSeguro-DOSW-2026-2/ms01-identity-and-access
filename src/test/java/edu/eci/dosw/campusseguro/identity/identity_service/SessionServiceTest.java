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
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionServiceImpl;

/**
 * Pruebas del cierre de sesión (RF-MS01-04), la regla central es que el logout queda auditado con el usuario
 * y la fecha, que no depende de la base de datos y que no cambia la respuesta si la auditoría falla
 */
class SessionServiceTest {

    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";
    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private AuditEventPublisher auditPublisher;
    private SessionServiceImpl sessionService;

    /**
     * Prepara el servicio con un publicador de auditoría simulado y un reloj fijo
     */
    @BeforeEach
    void setUp() {
        auditPublisher = mock(AuditEventPublisher.class);
        sessionService = serviceWith(auditPublisher);
    }

    /**
     * Construye el servicio con el publicador indicado y un reloj fijo en UTC
     *
     * @param publisher publicador de eventos de auditoría
     * @return el servicio configurado
     */
    private SessionServiceImpl serviceWith(AuditEventPublisher publisher) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new SessionServiceImpl(new AuditRecorder(publisher, clock));
    }

    /**
     * Construye la identidad de un solicitante con los roles indicados
     *
     * @param id identificador del usuario
     * @param roles roles del usuario
     * @return la identidad del solicitante
     */
    private RequesterIdentity requester(UUID id, Set<UserRole> roles) {
        return new RequesterIdentity(id, roles);
    }

    /**
     * Captura el único evento de auditoría emitido
     *
     * @return el evento emitido
     */
    private AuditEvent singleAuditEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(captor.capture());
        return captor.getValue();
    }

    /**
     * Un logout correcto emite un único evento LOGOUT exitoso con el usuario, el recurso, la fecha y el
     * correlation id
     */
    @Test
    void logout_validRequester_emitsOneSuccessfulLogoutEventWithUserDateAndCorrelationId() {
        UUID id = UUID.randomUUID();

        sessionService.logout(requester(id, EnumSet.of(UserRole.ESTUDIANTE)), CORRELATION_ID);

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
     * El logout funciona con cualquier rol del catálogo
     *
     * @param role rol del solicitante
     */
    @ParameterizedTest
    @EnumSource(UserRole.class)
    void logout_anyRole_isAcceptedAndAudited(UserRole role) {
        UUID id = UUID.randomUUID();

        assertDoesNotThrow(() -> sessionService.logout(requester(id, EnumSet.of(role)), CORRELATION_ID));

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(role.name(), event.usuarioRoles());
    }

    /**
     * Con varios roles el evento propaga los roles tal como viajan en X-User-Role, en orden alfabético
     */
    @Test
    void logout_severalRoles_propagatesThemInAlphabeticalOrder() {
        RequesterIdentity identity = requester(UUID.randomUUID(),
                EnumSet.of(UserRole.ESTUDIANTE, UserRole.BRIGADISTA));

        sessionService.logout(identity, CORRELATION_ID);

        assertEquals("BRIGADISTA,ESTUDIANTE", singleAuditEvent().usuarioRoles());
    }

    /**
     * Si no llega el correlation id se genera uno nuevo y válido para el evento
     */
    @Test
    void logout_withoutCorrelationId_generatesOneForTheEvent() {
        sessionService.logout(requester(UUID.randomUUID(), EnumSet.of(UserRole.ADMIN)), null);

        String generated = singleAuditEvent().correlationId();
        assertEquals(generated, UUID.fromString(generated).toString());
        assertEquals('4', generated.charAt(14));
    }

    /**
     * Un correlation id que no es un UUID v4 se reemplaza por uno válido
     */
    @Test
    void logout_withInvalidCorrelationId_replacesItWithAValidOne() {
        sessionService.logout(requester(UUID.randomUUID(), EnumSet.of(UserRole.ADMIN)), "no-es-un-uuid");

        String used = singleAuditEvent().correlationId();
        assertNotEquals("no-es-un-uuid", used);
        assertEquals(used, UUID.fromString(used).toString());
    }

    /**
     * Si la auditoría falla el logout se completa igual, la respuesta al usuario no cambia
     */
    @Test
    void logout_whenTheAuditFails_stillCompletes() {
        AuditEventPublisher failing = mock(AuditEventPublisher.class);
        doThrow(new IllegalStateException("MS08 no disponible")).when(failing).publish(any(AuditEvent.class));
        SessionServiceImpl service = serviceWith(failing);

        assertDoesNotThrow(() -> service.logout(requester(UUID.randomUUID(), EnumSet.of(UserRole.ESTUDIANTE)),
                CORRELATION_ID));

        verify(failing).publish(any(AuditEvent.class));
    }

    /**
     * Cerrar sesión varias veces es válido: el token no se revoca, así que cada cierre se audita
     */
    @Test
    void logout_calledTwice_auditsEachCall() {
        RequesterIdentity identity = requester(UUID.randomUUID(), EnumSet.of(UserRole.PROFESOR));

        sessionService.logout(identity, CORRELATION_ID);
        sessionService.logout(identity, CORRELATION_ID);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(2)).publish(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(event -> event.resultado() == AuditResult.EXITO));
    }

    /**
     * Un solicitante sin ningún permiso se rechaza con 403 y se audita el fallo con un motivo genérico
     */
    @Test
    void logout_withoutPermission_isRejectedAndTheFailureIsAudited() {
        UUID id = UUID.randomUUID();
        RequesterIdentity withoutPermissions = requester(id, EnumSet.noneOf(UserRole.class));

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
     * Un rechazo por falta de permiso emite un solo evento y nunca uno exitoso
     */
    @Test
    void logout_withoutPermission_neverEmitsASuccessfulEvent() {
        RequesterIdentity withoutPermissions = requester(UUID.randomUUID(), EnumSet.noneOf(UserRole.class));

        assertThrows(ForbiddenOperationException.class,
                () -> sessionService.logout(withoutPermissions, CORRELATION_ID));

        assertNotEquals(AuditResult.EXITO, singleAuditEvent().resultado());
        verifyNoMoreInteractions(auditPublisher);
    }

    /**
     * El evento no contiene tokens, contraseñas ni datos personales: solo ids, roles y códigos
     */
    @Test
    void logout_event_containsNoSecretsOrPersonalData() {
        UUID id = UUID.randomUUID();

        sessionService.logout(requester(id, EnumSet.of(UserRole.ESTUDIANTE)), CORRELATION_ID);

        AuditEvent event = singleAuditEvent();
        String content = event.toString().toLowerCase();
        assertTrue(content.contains(id.toString()));
        assertFalse(content.contains("token"));
        assertFalse(content.contains("password"));
        assertFalse(content.contains("contrasena"));
        assertFalse(content.contains("@"));
    }
}
