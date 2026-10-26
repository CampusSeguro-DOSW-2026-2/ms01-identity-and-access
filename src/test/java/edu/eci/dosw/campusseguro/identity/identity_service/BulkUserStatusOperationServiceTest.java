package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.EnumSet;
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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * Pruebas del cambio de estado de usuarios (RF-MS01-07, formulario F-08)
 */
class BulkUserStatusOperationServiceTest {

    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    private UserRepository userRepository;
    private AuditEventPublisher auditPublisher;
    private UserServiceImpl userService;

    private final UUID adminId = UUID.randomUUID();
    private final RequesterIdentity admin = new RequesterIdentity(adminId, EnumSet.of(UserRole.ADMIN));

    /**
     * Prepara el servicio con sus dependencias simuladas
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        auditPublisher = mock(AuditEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        AuditRecorder auditRecorder = new AuditRecorder(auditPublisher, clock);
        RequestValidator requestValidator = new RequestValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
        userService = new UserServiceImpl(userRepository, mock(PasswordEncoder.class), auditRecorder,
                requestValidator, List.of("escuelaing.edu.co"));
    }

    /**
     * Construye un usuario de prueba
     *
     * @param id identificador del usuario
     * @param state estado inicial del usuario
     * @return el usuario de prueba
     */
    private UserEntity user(UUID id, UserState state) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setState(state);
        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE));
        return user;
    }

    /**
     * Construye la petición de cambio de estado
     *
     * @param estado estado solicitado
     * @param ids identificadores de los usuarios a modificar
     * @return la petición de cambio de estado
     */
    private ChangeStatusRequest request(String estado, UUID... ids) {
        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setIds(java.util.Arrays.stream(ids)
                .map(UUID::toString)
                .toList());
        request.setEstado(estado);
        return request;
    }

    /**
     * Obtiene los eventos de auditoría publicados
     *
     * @param expected cantidad de eventos que se espera haber publicado
     * @return los eventos de auditoría en el orden en que se publicaron
     */
    private List<AuditEvent> auditEvents(int expected) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(expected)).publish(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Una petición válida actualiza a cada usuario e informa su estado anterior y el nuevo
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
     * Un cambio exitoso audita a cada usuario con su estado anterior y el nuevo
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
     * Sin permiso la operación se prohíbe, se audita y no toca la base de datos
     */
    @Test
    void changeUserStatus_withoutPermission_isForbiddenAuditedAndDoesNotTouchTheDatabase() {
        RequesterIdentity auditor = new RequesterIdentity(UUID.randomUUID(), EnumSet.of(UserRole.AUDITOR));

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
     * Incluir el propio identificador se prohíbe y se audita
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
     * Un usuario inexistente produce un no encontrado, se audita y no cambia nada
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
     * Un fallo de la base de datos audita a cada usuario y propaga el error
     */
    @Test
    void changeUserStatus_databaseFailure_auditsEachUserAndPropagatesTheError() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(userRepository.findAllByIdInOrderById(any()))
                .thenReturn(List.of(user(first, UserState.ACTIVO), user(second, UserState.ACTIVO)));
        when(userRepository.saveAllAndFlush(any())).thenThrow(new IllegalStateException("fallo simulado"));

        assertThrows(IllegalStateException.class,
                () -> userService.changeUserStatus(admin, request("INACTIVO", first, second), CORRELATION_ID));

        List<AuditEvent> events = auditEvents(2);
        assertTrue(events.stream().allMatch(event -> event.resultado() == AuditResult.FALLO));
        assertTrue(events.stream().allMatch(event -> "ERROR_INTERNO".equals(event.detalle())));
    }

    /**
     * Un cuerpo inválido informa los mensajes del formulario y no consulta la base de datos
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
     * Una lista de usuarios vacía se rechaza
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
     * Los identificadores repetidos se procesan una sola vez
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
