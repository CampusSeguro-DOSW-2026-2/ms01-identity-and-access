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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserSearchValidator;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * Tests of the internal queries of users by id and by role (RF-MS01-09)
 */
class InternalUserLookupServiceTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private AuditEventPublisher auditPublisher;
    private UserServiceImpl userService;

        /**
     * Prepares the service with its simulated dependencies
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        when(roleRepository.existsByName("BRIGADISTA")).thenReturn(true);
        when(roleRepository.existsByName("ESTUDIANTE")).thenReturn(true);
        auditPublisher = mock(AuditEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        RequestValidator requestValidator = new RequestValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
        userService = new UserServiceImpl(userRepository, roleRepository, mock(PasswordEncoder.class),
                new AuditRecorder(auditPublisher, clock), requestValidator, FixedDatabaseTime.at(Instant.parse("2026-03-01T10:00:00Z")), List.of("escuelaing.edu.co"));
    }

        /**
     * Builds a test user
     *
     * @param id id of the user
     * @param state status of the user
     * @param roles roles of the user
     * @return the test user
     */
    private static UserEntity user(UUID id, UserState state, String... roles) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setFirstName("Laura");
        u.setFirstLastName("Cardenas");
        u.setEmail("laura." + id.toString().substring(0, 8) + "@escuelaing.edu.co");
        u.setPassword("$2a$10$hash");
        u.setRoles(TestEntities.roles(roles));
        u.setState(state);
        return u;
    }

        /**
     * Builds the batch body with the indicated ids
     *
     * @param ids ids as text
     * @return the batch body
     */
    private static BatchUsersRequest batch(List<String> ids) {
        BatchUsersRequest request = new BatchUsersRequest();
        request.setIds(ids);
        return request;
    }

        /**
     * Gets the error message of the ids field
     *
     * @param e validation exception
     * @return the message of the ids field
     */
    private static String idsMessage(InvalidRequestException e) {
        return e.getErrors().stream()
                .filter(error -> error.campo().equals("ids"))
                .map(ErrorResponse.ErrorDetail::mensaje)
                .findFirst()
                .orElseThrow();
    }

        /**
     * The batch returns the users in the requested order, without repeated ones, and reports those that do not exist
     */
    @Test
    void findUsersByIds_keepsTheRequestOrderAndReportsTheMissingIds() {
        UUID first = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(
                user(second, UserState.ACTIVO, "ESTUDIANTE"),
                user(first, UserState.ACTIVO, "BRIGADISTA")));

        BatchUsersResponse response = userService.findUsersByIds(batch(List.of(
                first.toString(), missing.toString(), second.toString(), first.toString())));

        assertEquals(List.of(first, second), response.usuarios().stream().map(UserProfileResponse::id).toList());
        assertEquals(List.of(missing), response.noEncontrados());
    }

        /**
     * The batch queries each id only once
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
     * The batch includes the inactive users with their status, the caller decides whether to use them
     */
    @Test
    void findUsersByIds_includesInactiveUsersWithTheirState() {
        UUID id = UUID.randomUUID();
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(user(id, UserState.INACTIVO, "ESTUDIANTE")));

        BatchUsersResponse response = userService.findUsersByIds(batch(List.of(id.toString())));

        assertEquals(UserState.INACTIVO, response.usuarios().get(0).state());
    }

        /**
     * The batch does not audit the reads
     */
    @Test
    void findUsersByIds_isNotAudited() {
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());

        userService.findUsersByIds(batch(List.of(UUID.randomUUID().toString())));

        verifyNoInteractions(auditPublisher);
    }

        /**
     * An absent or empty list is rejected with the message of the contract
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
     * More than 100 ids are rejected with the message of the contract
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
     * An id that is not a UUID is rejected with the message of the contract
     */
    @Test
    void findUsersByIds_invalidId_isRejected() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> userService.findUsersByIds(batch(List.of(UUID.randomUUID().toString(), "usr-001"))));

        assertEquals(BatchUsersRequest.INVALID_ID_MESSAGE, idsMessage(e));
    }

        /**
     * A field outside the contract is rejected
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
     * The query by role sorts by ascending id and uses the requested pagination
     */
    @Test
    void searchByRole_withoutCutoff_ordersByIdAscending() {
        when(userRepository.findByRole(eq("BRIGADISTA"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user(UUID.randomUUID(), UserState.ACTIVO, "BRIGADISTA")),
                        PageRequest.of(2, 100), 201));

        PageResponse<UserProfileResponse> page = userService.searchByRole("BRIGADISTA", null, "2", "100");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findByRole(eq("BRIGADISTA"), captor.capture());
        assertEquals(PageRequest.of(2, 100, Sort.by(Sort.Order.asc("id"))), captor.getValue());
        assertEquals(201, page.totalElementos());
        assertEquals(3, page.totalPaginas());
        verify(userRepository, never()).findByRoleCreatedUpTo(any(), any(), any());
    }

        /**
     * With a cut-off date the query by role filters by creation
     */
    @Test
    void searchByRole_withCutoff_usesTheCutoffQuery() {
        Instant cutoff = Instant.parse("2026-11-07T15:04:05Z");
        when(userRepository.findByRoleCreatedUpTo(eq("ESTUDIANTE"), eq(cutoff), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<UserProfileResponse> page = userService.searchByRole("ESTUDIANTE", "2026-11-07T15:04:05Z",
                null, null);

        assertEquals(0, page.totalElementos());
        assertEquals(List.of(), page.contenido());
        verify(userRepository, never()).findByRole(any(), any());
    }

        /**
     * The query by role returns all the statuses and is not audited
     */
    @Test
    void searchByRole_returnsEveryStateAndIsNotAudited() {
        when(userRepository.findByRole(eq("BRIGADISTA"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        user(UUID.randomUUID(), UserState.ACTIVO, "BRIGADISTA"),
                        user(UUID.randomUUID(), UserState.INACTIVO, "BRIGADISTA")),
                        PageRequest.of(0, 20), 2));

        PageResponse<UserProfileResponse> page = userService.searchByRole("BRIGADISTA", null, null, null);

        assertEquals(List.of(UserState.ACTIVO, UserState.INACTIVO),
                page.contenido().stream().map(UserProfileResponse::state).toList());
        verifyNoInteractions(auditPublisher);
    }

        /**
     * A role without the stored format is rejected without querying roles or users
     */
    @Test
    void searchByRole_roleWithoutTheStoredFormat_isRejectedWithoutQuerying() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> userService.searchByRole("super-usuario", null, null, null));

        assertEquals(UserSearchValidator.INVALID_ROLE_MESSAGE, e.getErrors().get(0).mensaje());
        verify(roleRepository, never()).existsByName(any());
        verify(userRepository, never()).findByRole(any(), any());
    }

        /**
     * A role with a valid format that does not exist in the roles table is rejected without looking up users
     */
    @Test
    void searchByRole_roleThatDoesNotExist_isRejectedWithoutQueryingUsers() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> userService.searchByRole("SUPERUSUARIO", null, null, null));

        assertEquals("Datos inválidos.", e.getMessage());
        assertEquals("rol", e.getErrors().get(0).campo());
        assertEquals(UserSearchValidator.INVALID_ROLE_MESSAGE, e.getErrors().get(0).mensaje());
        verify(roleRepository).existsByName("SUPERUSUARIO");
        verify(userRepository, never()).findByRole(any(), any());
        verify(userRepository, never()).findByRoleCreatedUpTo(any(), any(), any());
    }

        /**
     * The role is required in the internal query
     */
    @Test
    void searchByRole_missingRole_isRejected() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> userService.searchByRole(null, null, null, null));

        assertEquals(UserSearchValidator.INVALID_ROLE_MESSAGE, e.getErrors().get(0).mensaje());
        verify(roleRepository, never()).existsByName(any());
    }
}
