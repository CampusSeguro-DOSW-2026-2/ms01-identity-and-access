package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * Tests of the status change of users (RF-MS01-07, form F-08)
 */
class BulkUserStatusOperationServiceTest {

    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    private UserRepository userRepository;
    private AuditEventPublisher auditPublisher;
    private UserServiceImpl userService;

    private final UUID adminId = UUID.randomUUID();
    private final RequesterIdentity admin = TestIdentities.of(adminId, "ADMIN");

        /**
     * Prepares the service with its simulated dependencies
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        auditPublisher = mock(AuditEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        AuditRecorder auditRecorder = new AuditRecorder(auditPublisher, clock);
        RequestValidator requestValidator = new RequestValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
        userService = new UserServiceImpl(userRepository, mock(RoleRepository.class), mock(PasswordEncoder.class),
                auditRecorder, requestValidator, FixedDatabaseTime.at(Instant.parse("2026-03-01T10:00:00Z")), List.of("escuelaing.edu.co"));
    }

        /**
     * Builds a test user
     *
     * @param id identifier of the user
     * @param state initial status of the user
     * @return the test user
     */
    private UserEntity user(UUID id, UserState state) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setState(state);
        user.setRoles(TestEntities.roles("ESTUDIANTE"));
        return user;
    }

        /**
     * Builds the status change request
     *
     * @param estado requested status
     * @param ids identifiers of the users to modify
     * @return the status change request
     */
    private ChangeStatusRequest request(String estado, UUID... ids) {
        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setIds(Arrays.stream(ids)
                .map(UUID::toString)
                .toList());
        request.setEstado(estado);
        return request;
    }

        /**
     * Gets the published audit events
     *
     * @param expected number of events expected to have been published
     * @return the audit events in the order in which they were published
     */
    private List<AuditEvent> auditEvents(int expected) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(expected)).publish(captor.capture());
        return captor.getAllValues();
    }

        /**
     * A valid request updates each user and reports their previous and new status
     */
    @Test
    void changeUserStatus_validRequest_updatesEveryUserAndReportsPreviousAndNewState() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UserEntity firstUser = user(first, UserState.ACTIVO);
        UserEntity secondUser = user(second, UserState.INACTIVO);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(firstUser, secondUser));

        ChangeStatusResponse response = userService.changeUserStatus(admin, request("INACTIVO", second, first),
                CORRELATION_ID);

        assertEquals(2, response.resultados().size());
        assertEquals(second, response.resultados().get(0).id());
        assertEquals(UserState.INACTIVO, response.resultados().get(0).estadoAnterior());
        assertEquals(UserState.INACTIVO, response.resultados().get(0).estadoNuevo());
        assertEquals(first, response.resultados().get(1).id());
        assertEquals(UserState.ACTIVO, response.resultados().get(1).estadoAnterior());
        assertEquals(UserState.INACTIVO, response.resultados().get(1).estadoNuevo());
        assertEquals(UserState.INACTIVO, firstUser.getState());
        verify(userRepository).saveAllAndFlush(any());
    }

        /**
     * Each processed user receives the session cut with the time of the clock truncated to seconds (RF-MS01-11)
     */
    @Test
    void changeUserStatus_setsTheSessionCutOnEveryTarget() {
        UserEntity firstUser = user(UUID.randomUUID(), UserState.ACTIVO);
        UserEntity secondUser = user(UUID.randomUUID(), UserState.INACTIVO);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(firstUser, secondUser));

        userService.changeUserStatus(admin, request("INACTIVO", firstUser.getId(), secondUser.getId()),
                CORRELATION_ID);

        assertEquals(Instant.parse("2026-03-01T10:00:00Z"), firstUser.getValidFrom());
        assertEquals(Instant.parse("2026-03-01T10:00:00Z"), secondUser.getValidFrom());
    }

        /**
     * If the user already had the requested status the session cut is also recorded, the change is an account one
     */
    @Test
    void changeUserStatus_sameStateStillSetsTheSessionCut() {
        UserEntity target = user(UUID.randomUUID(), UserState.ACTIVO);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        userService.changeUserStatus(admin, request("ACTIVO", target.getId()), CORRELATION_ID);

        assertEquals(Instant.parse("2026-03-01T10:00:00Z"), target.getValidFrom());
    }

        /**
     * A rejected change does not record any session cut
     */
    @Test
    void changeUserStatus_rejectedChange_setsNoSessionCut() {
        UserEntity target = user(UUID.randomUUID(), UserState.ACTIVO);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(target));

        assertThrows(UsersNotFoundException.class, () -> userService.changeUserStatus(admin,
                request("INACTIVO", target.getId(), UUID.randomUUID()), CORRELATION_ID));

        assertNull(target.getValidFrom());
    }

        /**
     * A successful change audits each user with their previous and new status
     */
    @Test
    void changeUserStatus_success_auditsEachUserWithPreviousAndNewState() {
        UUID target = UUID.randomUUID();
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(user(target, UserState.ACTIVO)));

        userService.changeUserStatus(admin, request("INACTIVO", target), CORRELATION_ID);

        AuditEvent event = auditEvents(1).get(0);
        assertEquals(AuditAction.CAMBIO_ESTADO, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(adminId, event.usuarioId());
        assertEquals("ADMIN", event.usuarioRoles());
        assertEquals("usuario:" + target, event.recursoAfectado());
        assertEquals("estado_anterior=ACTIVO;estado_nuevo=INACTIVO", event.detalle());
        assertEquals(CORRELATION_ID, event.correlationId());
    }

        /**
     * Without permission the operation is forbidden, audited and does not touch the database
     */
    @Test
    void changeUserStatus_withoutPermission_isForbiddenAuditedAndDoesNotTouchTheDatabase() {
        RequesterIdentity auditor = TestIdentities.of(UUID.randomUUID(), "AUDITOR");

        ForbiddenOperationException error = assertThrows(ForbiddenOperationException.class,
                () -> userService.changeUserStatus(auditor, request("INACTIVO", UUID.randomUUID()),
                        CORRELATION_ID));

        assertEquals("No cuenta con los permisos suficientes para realizar esta operación.", error.getMessage());
        verify(userRepository, never()).findAllByIdInOrderById(any());
        AuditEvent event = auditEvents(1).get(0);
        assertEquals(AuditAction.CAMBIO_ESTADO, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("SIN_PERMISO", event.detalle());
    }

        /**
     * Including the own identifier is forbidden and audited
     */
    @Test
    void changeUserStatus_includingOwnId_isForbiddenAndAudited() {
        ForbiddenOperationException error = assertThrows(ForbiddenOperationException.class,
                () -> userService.changeUserStatus(admin, request("INACTIVO", adminId, UUID.randomUUID()),
                        CORRELATION_ID));

        assertEquals("No puede cambiar su propio estado.", error.getMessage());
        verify(userRepository, never()).findAllByIdInOrderById(any());
        AuditEvent event = auditEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("AUTOMODIFICACION_DE_ESTADO", event.detalle());
        assertEquals("usuario:" + adminId, event.recursoAfectado());
    }

        /**
     * A non-existent user produces a not found, is audited and changes nothing
     */
    @Test
    void changeUserStatus_unknownUser_isNotFoundAuditedAndChangesNothing() {
        UUID existing = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UserEntity existingUser = user(existing, UserState.ACTIVO);
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(existingUser));

        UsersNotFoundException error = assertThrows(UsersNotFoundException.class,
                () -> userService.changeUserStatus(admin, request("INACTIVO", existing, missing),
                        CORRELATION_ID));

        assertEquals(List.of(missing), error.getMissingIds());
        assertEquals(UserState.ACTIVO, existingUser.getState());
        verify(userRepository, never()).saveAllAndFlush(any());
        AuditEvent event = auditEvents(1).get(0);
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("USUARIO_NO_ENCONTRADO", event.detalle());
        assertEquals("usuario:" + missing, event.recursoAfectado());
    }

        /**
     * A database failure audits each user and propagates the error
     */
    @Test
    void changeUserStatus_databaseFailure_auditsEachUserAndPropagatesTheError() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(userRepository.findAllByIdInOrderById(any()))
                .thenReturn(List.of(user(first, UserState.ACTIVO), user(second, UserState.ACTIVO)));
        when(userRepository.saveAllAndFlush(any())).thenThrow(new IllegalStateException("simulated failure"));

        assertThrows(IllegalStateException.class,
                () -> userService.changeUserStatus(admin, request("INACTIVO", first, second), CORRELATION_ID));

        List<AuditEvent> events = auditEvents(2);
        assertTrue(events.stream().allMatch(event -> event.resultado() == AuditResult.FALLO));
        assertTrue(events.stream().allMatch(event -> "ERROR_INTERNO".equals(event.detalle())));
    }

        /**
     * An invalid body reports the messages of the form and does not query the database
     */
    @Test
    void changeUserStatus_invalidBody_reportsTheFormMessagesAndDoesNotQuery() {
        ChangeStatusRequest invalid = new ChangeStatusRequest();
        invalid.setIds(List.of("no-es-uuid"));
        invalid.setEstado("PENDIENTE");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.changeUserStatus(admin, invalid, CORRELATION_ID));

        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("estado", "Estado no válido.")));
        assertEquals(2, error.getErrors().size());
        verify(userRepository, never()).findAllByIdInOrderById(any());
    }

        /**
     * An empty list of users is rejected
     */
    @Test
    void changeUserStatus_emptyUserList_isRejected() {
        ChangeStatusRequest invalid = new ChangeStatusRequest();
        invalid.setIds(List.of());
        invalid.setEstado("ACTIVO");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.changeUserStatus(admin, invalid, CORRELATION_ID));

        assertEquals("ids", error.getErrors().get(0).campo());
        assertEquals("Seleccione al menos un usuario.", error.getErrors().get(0).mensaje());
    }

        /**
     * Repeated identifiers are processed only once
     */
    @Test
    void changeUserStatus_duplicatedIds_areProcessedOnce() {
        UUID target = UUID.randomUUID();
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(user(target, UserState.INACTIVO)));

        ChangeStatusResponse response = userService.changeUserStatus(admin,
                request("ACTIVO", target, target), CORRELATION_ID);

        assertEquals(1, response.resultados().size());
        auditEvents(1);
    }
}
