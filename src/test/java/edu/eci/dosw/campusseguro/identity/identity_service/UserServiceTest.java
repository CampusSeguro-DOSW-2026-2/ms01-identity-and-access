package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * Tests of the registration (RF-MS01-01), the own profile (RF-MS01-06) and the query by id (RF-MS01-07)
 */
class UserServiceTest {

    private static final List<String> DOMAINS = List.of("escuelaing.edu.co", "mail.escuelaing.edu.co");
    private static final String PASSWORD = "MiPassword#2026";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private AuditEventPublisher auditPublisher;
    private UserServiceImpl userService;

        /**
     * Prepares the service with its simulated dependencies
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        when(roleRepository.findByName("ESTUDIANTE"))
                .thenReturn(Optional.of(TestEntities.role("ESTUDIANTE", "ms01:profile:read")));
        passwordEncoder = mock(PasswordEncoder.class);
        auditPublisher = mock(AuditEventPublisher.class);
        userService = serviceWithDomains(DOMAINS);
    }

        /**
     * Builds the service with the simulated dependencies and the indicated institutional domains
     *
     * @param domains institutional domains with which the service is configured
     * @return the configured service
     */
    private UserServiceImpl serviceWithDomains(List<String> domains) {
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        AuditRecorder auditRecorder = new AuditRecorder(auditPublisher, clock);
        RequestValidator requestValidator = new RequestValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
        return new UserServiceImpl(userRepository, roleRepository, passwordEncoder, auditRecorder, requestValidator,
                FixedDatabaseTime.at(Instant.parse("2026-03-01T10:00:00Z")), domains);
    }

        /**
     * Builds a valid registration request
     *
     * @param email email of the request
     * @return the registration request
     */
    private UserCreateRequest registrationRequest(String email) {
        UserCreateRequest request = new UserCreateRequest();
        request.setFirstName("Carlos");
        request.setSecondName("Andres");
        request.setFirstLastName("Gomez");
        request.setSecondLastName("Perez");
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setPasswordConfirmation(PASSWORD);
        return request;
    }

        /**
     * Builds a sample stored user
     *
     * @param id identifier of the user
     * @param email email of the user
     * @return the sample user
     */
    private UserEntity entity(UUID id, String email) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setFirstName("Carlos");
        entity.setFirstLastName("Gomez");
        entity.setEmail(email);
        entity.setPassword("$2a$10$hash");
        entity.setRoles(TestEntities.roles("ESTUDIANTE"));
        entity.setState(UserState.ACTIVO);
        return entity;
    }

        /**
     * Builds the identity of a requester
     *
     * @param id identifier of the requester
     * @param roles roles of the requester
     * @return the identity of the requester
     */
    private RequesterIdentity identity(UUID id, String... roles) {
        return TestIdentities.of(id, roles);
    }

        /**
     * Gets the only audit event that was published
     *
     * @return the published audit event
     */
    private AuditEvent singleAuditEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(captor.capture());
        return captor.getValue();
    }

        /**
     * A valid registration stores the user with the encrypted password and the default values
     */
    @Test
    void create_validRequest_savesUserWithHashedPasswordAndDefaults() {
        UUID id = UUID.randomUUID();
        UserCreateRequest request = registrationRequest("  Carlos.Gomez@EscuelaIng.edu.co  ");

        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        UserResponse response = userService.create(request, CORRELATION_ID);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserEntity saved = captor.getValue();

        assertEquals("carlos.gomez@escuelaing.edu.co", saved.getEmail());
        assertEquals("$2a$10$hash", saved.getPassword());
        assertNotEquals(PASSWORD, saved.getPassword());
        assertEquals(List.of("ESTUDIANTE"), saved.getRoleNames());
        assertEquals(UserState.ACTIVO, saved.getState());

        assertEquals(id, response.id());
        assertEquals("carlos.gomez@escuelaing.edu.co", response.email());
        assertEquals(List.of("ESTUDIANTE"), response.roles());
        assertEquals(UserState.ACTIVO, response.state());
    }

        /**
     * If the ESTUDIANTE system role does not exist in the roles table the registration fails without saving anything
     */
    @Test
    void create_studentRoleMissingInTheDatabase_failsWithoutSaving() {
        when(roleRepository.findByName("ESTUDIANTE")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");

        assertThrows(IllegalStateException.class,
                () -> userService.create(registrationRequest("carlos.gomez@escuelaing.edu.co"), CORRELATION_ID));

        verify(userRepository, never()).saveAndFlush(any());
    }

        /**
     * A successful registration is audited without personal data
     */
    @Test
    void create_success_auditsRegistroWithoutPersonalData() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        userService.create(registrationRequest("carlos.gomez@escuelaing.edu.co"), CORRELATION_ID);

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals("usuario:" + id, event.recursoAfectado());
        assertEquals(CORRELATION_ID, event.correlationId());
        assertNull(event.usuarioId());
        assertNull(event.usuarioRoles());
        assertNull(event.detalle());
    }

        /**
     * The blank optional names are stored as null
     */
    @Test
    void create_blankOptionalNames_areSavedAsNull() {
        UserCreateRequest request = registrationRequest("carlos.gomez@escuelaing.edu.co");
        request.setSecondName("   ");
        request.setSecondLastName("");

        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, CORRELATION_ID);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getSecondName());
        assertNull(captor.getValue().getSecondLastName());
    }

        /**
     * An already registered email throws the conflict, is audited and is not saved
     */
    @Test
    void create_emailAlreadyExists_throwsAuditsAndDoesNotSave() {
        UserCreateRequest request = registrationRequest("Carlos.Gomez@escuelaing.edu.co");
        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> userService.create(request, CORRELATION_ID));

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        verify(passwordEncoder, never()).encode(any());
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CONFLICTO", event.detalle());
        assertNull(event.recursoAfectado());
    }

        /**
     * A violation of the uniqueness constraint is reported as an email conflict
     */
    @Test
    void create_uniqueConstraintViolation_isReportedAsEmailConflict() {
        UserCreateRequest request = registrationRequest("carlos.gomez@escuelaing.edu.co");
        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThrows(EmailAlreadyExistsException.class, () -> userService.create(request, CORRELATION_ID));

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CONFLICTO", event.detalle());
    }

        /**
     * A domain outside the institution is rejected with the generic email message
     */
    @Test
    void create_foreignDomain_isRejectedWithGenericEmailMessage() {
        UserCreateRequest request = registrationRequest("carlos.gomez@gmail.com");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.create(request, CORRELATION_ID));

        assertEquals(1, error.getErrors().size());
        assertEquals("correo", error.getErrors().get(0).campo());
        assertEquals("Correo no válido.", error.getErrors().get(0).mensaje());
        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * The email of each configured institutional domain is accepted
     *
     * @param email email with the institutional domain that is tested
     */
    @ParameterizedTest
    @ValueSource(strings = { "carlos.gomez@escuelaing.edu.co", "carlos.gomez@mail.escuelaing.edu.co" })
    void create_emailOfEachConfiguredDomain_isAccepted(String email) {
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        UserResponse response = userService.create(registrationRequest(email), CORRELATION_ID);

        assertEquals(email, response.email());
        assertEquals(id, response.id());
    }

        /**
     * A domain that is not equal to any of the configured ones is rejected, including subdomains and domains that only
     * contain one of the configured ones
     *
     * @param email email with a domain that is not allowed
     */
    @ParameterizedTest
    @ValueSource(strings = { "carlos.gomez@otro.escuelaing.edu.co", "carlos.gomez@mail.mail.escuelaing.edu.co",
            "carlos.gomez@escuelaing.edu.co.ejemplo.com", "carlos.gomez@escuelaing.edu", "carlos.gomez@gmail.com" })
    void create_domainNotInTheConfiguredList_isRejected(String email) {
        UserCreateRequest request = registrationRequest(email);

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.create(request, CORRELATION_ID));

        assertEquals("correo", error.getErrors().get(0).campo());
        assertEquals("Correo no válido.", error.getErrors().get(0).mensaje());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * The configured domains are normalized: spaces, uppercase and empty values are ignored
     */
    @Test
    void create_configuredDomainsAreNormalized() {
        UserServiceImpl service = serviceWithDomains(List.of(" Mail.EscuelaIng.edu.co ", "", "   "));
        UserCreateRequest rejected = registrationRequest("carlos.gomez@escuelaing.edu.co");
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail("carlos.gomez@mail.escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        UserResponse accepted = service.create(registrationRequest("carlos.gomez@mail.escuelaing.edu.co"),
                CORRELATION_ID);

        assertEquals("carlos.gomez@mail.escuelaing.edu.co", accepted.email());
        assertThrows(InvalidRequestException.class, () -> service.create(rejected, CORRELATION_ID));
    }

        /**
     * Invalid data is audited as a failure and all the fields are reported
     */
    @Test
    void create_invalidData_auditsFalloAndReportsEveryField() {
        UserCreateRequest request = registrationRequest("no-es-un-correo");
        request.setFirstName("A");
        request.setPasswordConfirmation("Otra#Clave2026");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.create(request, CORRELATION_ID));

        List<String> fields = error.getErrors().stream()
                .map(ErrorResponse.ErrorDetail::campo)
                .toList();
        assertTrue(fields.contains("correo"));
        assertTrue(fields.contains("primer_nombre"));
        assertTrue(fields.contains("confirmar_contrasena"));
        assertEquals(fields.stream().distinct().count(), fields.size());

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("VALIDACION", event.detalle());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * A failure of the audit does not change the response of the registration
     */
    @Test
    void create_auditFailure_doesNotChangeTheResponse() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });
        doThrow(new IllegalStateException("MS08 down")).when(auditPublisher).publish(any(AuditEvent.class));

        UserResponse response = userService.create(registrationRequest("carlos.gomez@escuelaing.edu.co"),
                CORRELATION_ID);

        assertEquals(id, response.id());
    }

        /**
     * The query of the own profile returns the profile of the user
     */
    @Test
    void getMyProfile_existingUser_returnsProfile() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(entity(id, "carlos.gomez@escuelaing.edu.co")));

        UserProfileResponse result = userService.getMyProfile(identity(id, "ESTUDIANTE"));

        assertEquals("carlos.gomez@escuelaing.edu.co", result.email());
        assertEquals("Carlos", result.firstName());
    }

        /**
     * The query of the own profile of a non-existent user throws not found
     */
    @Test
    void getMyProfile_unknownUser_throwsUserNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.getMyProfile(identity(id, "ESTUDIANTE")));
    }

        /**
     * A valid update modifies and returns the profile
     */
    @Test
    void updateProfile_validRequest_updatesAndReturnsProfile() {
        UUID id = UUID.randomUUID();
        UserEntity existing = entity(id, "carlos.gomez@escuelaing.edu.co");
        UserUpdateProfileRequest request = new UserUpdateProfileRequest();
        request.setFirstName("Sebastian");
        request.setFirstLastName("Gomez");

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse result = userService.updateProfile(identity(id, "ESTUDIANTE"), request);

        assertEquals("Sebastian", result.firstName());
        assertEquals("carlos.gomez@escuelaing.edu.co", result.email());
        verify(userRepository).saveAndFlush(any(UserEntity.class));
    }

        /**
     * Invalid names throw invalid request and nothing is saved
     */
    @Test
    void updateProfile_invalidNames_throwsInvalidRequestAndDoesNotSave() {
        UUID id = UUID.randomUUID();
        UserUpdateProfileRequest request = new UserUpdateProfileRequest();
        request.setFirstName("1");
        request.setFirstLastName("Gomez");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.updateProfile(identity(id, "ESTUDIANTE"), request));

        assertEquals("primer_nombre", error.getErrors().get(0).campo());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * The rejected fields report the messages of the form
     */
    @Test
    void updateProfile_rejectedFields_reportTheFormMessages() {
        UUID id = UUID.randomUUID();
        UserUpdateProfileRequest request = new UserUpdateProfileRequest();
        request.setFirstName("Sebastian");
        request.setFirstLastName("Gomez");
        request.registerUnknownField("correo", "otro@escuelaing.edu.co");
        request.registerUnknownField("roles", List.of("ADMIN"));
        request.registerUnknownField("estado", "INACTIVO");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.updateProfile(identity(id, "ESTUDIANTE"), request));

        assertEquals(3, error.getErrors().size());
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("correo",
                "El correo no se puede modificar.")));
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("roles",
                "Los roles no se pueden modificar desde el perfil.")));
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("estado",
                "El estado no se puede modificar desde el perfil.")));
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * The query of a user by id with permission returns their profile
     */
    @Test
    void getUserById_withReadPermission_returnsProfile() {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(entity(targetId, "carlos.gomez@escuelaing.edu.co")));

        UserProfileResponse result = userService.getUserById(identity(requesterId, "AUDITOR"), targetId);

        assertEquals(targetId, result.id());
        assertEquals("carlos.gomez@escuelaing.edu.co", result.email());
    }

        /**
     * The query of a non-existent user throws not found
     */
    @Test
    void getUserById_unknownUser_throwsUserNotFoundException() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.getUserById(identity(UUID.randomUUID(), "ADMIN"), targetId));
    }

        /**
     * Without read permission the query is forbidden and the database is not queried
     */
    @Test
    void getUserById_withoutReadPermission_isForbiddenAndDoesNotQuery() {
        UUID targetId = UUID.randomUUID();

        assertThrows(ForbiddenOperationException.class,
                () -> userService.getUserById(identity(UUID.randomUUID(), "ESTUDIANTE"), targetId));

        verify(userRepository, never()).findById(any());
    }

        /**
     * The response includes the optional names only when they exist
     */
    @Test
    void getUserById_responseIncludesOptionalNamesOnlyWhenPresent() {
        UUID targetId = UUID.randomUUID();
        UserEntity target = entity(targetId, "carlos.gomez@escuelaing.edu.co");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        UserProfileResponse result = userService.getUserById(identity(UUID.randomUUID(), "ADMIN"),
                targetId);

        assertNull(result.secondName());
        assertNull(result.secondLastName());
        assertFalse(result.roles().isEmpty());
    }
}
