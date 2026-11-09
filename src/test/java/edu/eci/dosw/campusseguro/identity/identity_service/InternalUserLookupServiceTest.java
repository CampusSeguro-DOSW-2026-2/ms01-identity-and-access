package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserSearchValidator;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * Pruebas de las consultas internas de usuarios por id y por rol (RF-MS01-09)
 */
class InternalUserLookupServiceTest {

    private UserRepository userRepository;
    private AuditEventPublisher auditPublisher;
    private UserServiceImpl userService;

    /**
     * Prepara el servicio con sus dependencias simuladas
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        auditPublisher = mock(AuditEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        RequestValidator requestValidator = new RequestValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
        userService = new UserServiceImpl(userRepository, mock(PasswordEncoder.class),
                new AuditRecorder(auditPublisher, clock), requestValidator, List.of("escuelaing.edu.co"));
    }

    /**
     * Construye un usuario de prueba
     *
     * @param id id del usuario
     * @param state estado del usuario
     * @param roles roles del usuario
     * @return el usuario de prueba
     */
    private static UserEntity user(UUID id, UserState state, UserRole... roles) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setFirstName("Laura");
        u.setFirstLastName("Cardenas");
        u.setEmail("laura." + id.toString().substring(0, 8) + "@escuelaing.edu.co");
        u.setPassword("$2a$10$hash");
        u.setRoles(new HashSet<>(List.of(roles)));
        u.setState(state);
        return u;
    }

    /**
     * Construye el cuerpo del batch con los ids indicados
     *
     * @param ids ids como texto
     * @return el cuerpo del batch
     */
    private static BatchUsersRequest batch(List<String> ids) {
        BatchUsersRequest request = new BatchUsersRequest();
        request.setIds(ids);
        return request;
    }

    /**
     * Obtiene el mensaje del error del campo ids
     *
     * @param e excepción de validación
     * @return el mensaje del campo ids
     */
    private static String idsMessage(InvalidRequestException e) {
        return e.getErrors().stream()
                .filter(error -> error.campo().equals("ids"))
                .map(ErrorResponse.ErrorDetail::mensaje)
                .findFirst()
                .orElseThrow();
    }

    /**
     * El batch devuelve los usuarios en el orden pedido, sin repetidos, e informa los que no existen
     */
    @Test
    void findUsersByIds_keepsTheRequestOrderAndReportsTheMissingIds() {
        UUID first = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(
                user(second, UserState.ACTIVO, UserRole.ESTUDIANTE),
                user(first, UserState.ACTIVO, UserRole.BRIGADISTA)));

        BatchUsersResponse response = userService.findUsersByIds(batch(List.of(
                first.toString(), missing.toString(), second.toString(), first.toString())));

        assertEquals(List.of(first, second), response.usuarios().stream().map(UserProfileResponse::id).toList());
        assertEquals(List.of(missing), response.noEncontrados());
    }

    /**
     * El batch consulta cada id una sola vez
     */
    @Test
    @SuppressWarnings("unchecked")
    void findUsersByIds_queriesEachIdOnce() {
        UUID id = UUID.randomUUID();
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());

        userService.findUsersByIds(batch(List.of(id.toString(), " " + id + " ")));

        ArgumentCaptor<Iterable<UUID>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository).findAllById(captor.capture());
        List<UUID> queried = new ArrayList<>();
        captor.getValue().forEach(queried::add);
        assertEquals(List.of(id), queried);
    }

    /**
     * El batch incluye a los usuarios inactivos con su estado, el llamante decide si los usa
     */
    @Test
    void findUsersByIds_includesInactiveUsersWithTheirState() {
        UUID id = UUID.randomUUID();
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(user(id, UserState.INACTIVO, UserRole.ESTUDIANTE)));

        BatchUsersResponse response = userService.findUsersByIds(batch(List.of(id.toString())));

        assertEquals(UserState.INACTIVO, response.usuarios().get(0).state());
    }

    /**
     * El batch no audita las lecturas
     */
    @Test
    void findUsersByIds_isNotAudited() {
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());

        userService.findUsersByIds(batch(List.of(UUID.randomUUID().toString())));

        verifyNoInteractions(auditPublisher);
    }

    /**
     * Una lista ausente o vacía se rechaza con el mensaje del contrato
     */
    @Test
    void findUsersByIds_emptyOrMissingList_isRejected() {
        InvalidRequestException empty = assertThrows(InvalidRequestException.class,
                () -> userService.findUsersByIds(batch(List.of())));
        InvalidRequestException missing = assertThrows(InvalidRequestException.class,
                () -> userService.findUsersByIds(batch(null)));

        assertEquals(BatchUsersRequest.INVALID_LIST_MESSAGE, idsMessage(empty));
        assertEquals(BatchUsersRequest.INVALID_LIST_MESSAGE, idsMessage(missing));
        verify(userRepository, never()).findAllById(any());
    }

    /**
     * Más de 100 ids se rechazan con el mensaje del contrato
     */
    @Test
    void findUsersByIds_moreThan100Ids_isRejected() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < BatchUsersRequest.MAX_IDS + 1; i++) {
            ids.add(UUID.randomUUID().toString());
        }

        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> userService.findUsersByIds(batch(ids)));

        assertEquals(BatchUsersRequest.INVALID_LIST_MESSAGE, idsMessage(e));
    }

    /**
     * Un id que no es UUID se rechaza con el mensaje del contrato
     */
    @Test
    void findUsersByIds_invalidId_isRejected() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> userService.findUsersByIds(batch(List.of(UUID.randomUUID().toString(), "usr-001"))));

        assertEquals(BatchUsersRequest.INVALID_ID_MESSAGE, idsMessage(e));
    }

    /**
     * Un campo fuera del contrato se rechaza
     */
    @Test
    void findUsersByIds_unknownField_isRejected() {
        BatchUsersRequest request = batch(List.of(UUID.randomUUID().toString()));
        request.registerUnknownField("estado", "ACTIVO");

        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> userService.findUsersByIds(request));

        assertEquals("estado", e.getErrors().get(0).campo());
    }

    /**
     * La consulta por rol ordena por id ascendente y usa la paginación pedida
     */
    @Test
    void searchByRole_withoutCutoff_ordersByIdAscending() {
        when(userRepository.findByRole(eq(UserRole.BRIGADISTA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user(UUID.randomUUID(), UserState.ACTIVO, UserRole.BRIGADISTA)),
                        PageRequest.of(2, 100), 201));

        PageResponse<UserProfileResponse> page = userService.searchByRole("BRIGADISTA", null, "2", "100");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findByRole(eq(UserRole.BRIGADISTA), captor.capture());
        assertEquals(PageRequest.of(2, 100, Sort.by(Sort.Order.asc("id"))), captor.getValue());
        assertEquals(201, page.totalElementos());
        assertEquals(3, page.totalPaginas());
        verify(userRepository, never()).findByRoleCreatedUpTo(any(), any(), any());
    }

    /**
     * Con fecha de corte la consulta por rol filtra por creación
     */
    @Test
    void searchByRole_withCutoff_usesTheCutoffQuery() {
        Instant cutoff = Instant.parse("2026-11-07T15:04:05Z");
        when(userRepository.findByRoleCreatedUpTo(eq(UserRole.ESTUDIANTE), eq(cutoff), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<UserProfileResponse> page = userService.searchByRole("ESTUDIANTE", "2026-11-07T15:04:05Z",
                null, null);

        assertEquals(0, page.totalElementos());
        assertEquals(List.of(), page.contenido());
        verify(userRepository, never()).findByRole(any(), any());
    }

    /**
     * La consulta por rol devuelve todos los estados y no se audita
     */
    @Test
    void searchByRole_returnsEveryStateAndIsNotAudited() {
        when(userRepository.findByRole(eq(UserRole.BRIGADISTA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        user(UUID.randomUUID(), UserState.ACTIVO, UserRole.BRIGADISTA),
                        user(UUID.randomUUID(), UserState.INACTIVO, UserRole.BRIGADISTA)),
                        PageRequest.of(0, 20), 2));

        PageResponse<UserProfileResponse> page = userService.searchByRole("BRIGADISTA", null, null, null);

        assertEquals(List.of(UserState.ACTIVO, UserState.INACTIVO),
                page.contenido().stream().map(UserProfileResponse::state).toList());
        verifyNoInteractions(auditPublisher);
    }

    /**
     * Un rol inválido se rechaza sin consultar la base de datos
     */
    @Test
    void searchByRole_invalidRole_isRejectedWithoutQuerying() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> userService.searchByRole("SUPERUSUARIO", null, null, null));

        assertEquals(UserSearchValidator.INVALID_ROLE_MESSAGE, e.getErrors().get(0).mensaje());
        verify(userRepository, never()).findByRole(any(), any());
    }
}
