package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IssuedToken;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * RF-MS01-02: base flow, alternate flows A1 and A2, RN-MS01-05, RN-MS01-06 and audit of all the attempts
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00Z");
    private static final String DUMMY_HASH = "dummy-hash";
    private static final String STORED_HASH = "$2a$10$stored.hash.value";
    private static final String EMAIL = "maria.perez@escuelaing.edu.co";
    private static final String PASSWORD = "Campus#2026";
    private static final String TOKEN = "header.payload.signature";
    private static final String CORR = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";
    private static final String CORR_2 = "1c2f9d34-5b7a-4e0c-9d61-7a4b8e3f2a10";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditEventPublisher auditPublisher;

    private AuthServiceImpl authService;

        /**
     * Prepares the authentication service with its simulated dependencies
     */
    @BeforeEach
    void setUp() {
        // The service computes a padding hash when it is built (time equalization).
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, recorder(),
                requestValidator());
    }

        /**
     * Creates the audit recorder used by the service under test
     *
     * @return the audit recorder
     */
    private AuditRecorder recorder() {
        return new AuditRecorder(auditPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

        /**
     * Creates the request validator used by the service under test
     *
     * @return the request validator
     */
    private RequestValidator requestValidator() {
        return new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator());
    }

        /**
     * Builds a login request
     *
     * @param email email of the request
     * @param password password of the request
     * @return the login request
     */
    private LoginRequest request(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setCorreo(email);
        request.setContrasena(password);
        return request;
    }

        /**
     * Builds a test user with the expected password and roles that only have ms01:profile:read
     *
     * @param state status of the user
     * @param roles names of the roles of the user
     * @return the test user
     */
    private UserEntity user(UserState state, String... roles) {
        return user(state, TestEntities.roles(roles));
    }

        /**
     * Builds a test user with the expected password and the given stored roles
     *
     * @param state status of the user
     * @param roles roles of the user with their permissions
     * @return the test user
     */
    private UserEntity user(UserState state, Set<RoleEntity> roles) {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setFirstName("Maria");
        entity.setFirstLastName("Perez");
        entity.setEmail(EMAIL);
        entity.setPassword(STORED_HASH);
        entity.setRoles(roles);
        entity.setState(state);
        return entity;
    }

        /**
     * Gets the only audit event that was published
     *
     * @return the published audit event
     */
    private AuditEvent publishedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- base flow

        /**
     * An active user with the correct password receives the token and the success is audited
     */
    @Test
    void login_activeUserWithCorrectPassword_returnsTokenAndAuditsSuccess() {
        UserEntity user = user(UserState.ACTIVO, Set.of(
                TestEntities.role("ESTUDIANTE", "ms01:profile:read", "ms01:role:read_own"),
                TestEntities.role("PROFESOR", "ms05:incident:report", "ms01:profile:read")));
        IssuedToken issued = new IssuedToken(TOKEN, NOW.plusSeconds(3600));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(user.getId(), EMAIL, List.of("ESTUDIANTE", "PROFESOR"),
                List.of("ms01:profile:read", "ms01:role:read_own", "ms05:incident:report"))).thenReturn(issued);

        LoginResponse response = authService.login(request(EMAIL, PASSWORD), CORR);

        assertEquals(TOKEN, response.token());
        assertEquals("Bearer", response.tipo());
        assertEquals("2026-10-22T06:00:00Z", response.expiraEn());
        assertEquals(user.getId(), response.usuario().id());
        assertEquals(EMAIL, response.usuario().correo());
        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), response.usuario().roles());

        AuditEvent event = publishedEvent();
        assertEquals(AuditAction.LOGIN, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(user.getId(), event.usuarioId());
        assertEquals("usuario:" + user.getId(), event.recursoAfectado());
        assertNull(event.detalle());
        assertEquals(CORR, event.correlationId());
        assertEquals(NOW, event.fecha());
    }

        /**
     * The roles are sorted alphabetically in the token and in the response
     */
    @Test
    void login_rolesAreSortedAlphabeticallyInTokenAndResponse() {
        UserEntity user = user(UserState.ACTIVO, Set.of(
                TestEntities.role("PROFESOR", "ms01:profile:read"),
                TestEntities.role("ADMIN", "ms01:user:roles"),
                TestEntities.role("BRIGADISTA", "ms01:profile:read")));
        List<String> alphabetical = List.of("ADMIN", "BRIGADISTA", "PROFESOR");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(user.getId(), EMAIL, alphabetical,
                List.of("ms01:profile:read", "ms01:user:roles")))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        LoginResponse response = authService.login(request(EMAIL, PASSWORD), CORR);

        assertEquals(alphabetical, response.usuario().roles());
    }

        /**
     * The email is normalized before querying the user
     */
    @Test
    void login_emailIsNormalizedBeforeLookup() {
        UserEntity user = user(UserState.ACTIVO, "ESTUDIANTE");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request("  Maria.Perez@EscuelaIng.edu.co ", PASSWORD), CORR);

        verify(userRepository).findByEmail(EMAIL);
    }

        /**
     * The password is never trimmed or normalized
     */
    @Test
    void login_passwordIsNeverTrimmedOrNormalized() {
        UserEntity user = user(UserState.ACTIVO, "ESTUDIANTE");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(" Campus#2026 ", STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, " Campus#2026 "), CORR);

        verify(passwordEncoder).matches(" Campus#2026 ", STORED_HASH);
    }

        /**
     * The verification works with a real BCrypt hash like the one stored in the registration
     */
    @Test
    void login_verifiesAgainstRealBCryptHashLikeTheOneStoredAtRegistration() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        AuthServiceImpl service = new AuthServiceImpl(userRepository, bcrypt, jwtService, recorder(),
                requestValidator());
        UserEntity user = user(UserState.ACTIVO, "ESTUDIANTE");
        user.setPassword(bcrypt.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        assertEquals(TOKEN, service.login(request(EMAIL, PASSWORD), CORR).token());
        assertThrows(InvalidCredentialsException.class, () -> service.login(request(EMAIL, "Otra#2026x"), CORR_2));
    }

        /**
     * The permissions of the token are the union of those of all the roles of the user, without repeated ones and in
     * alphabetical order
     */
    @Test
    void login_tokenPermissionsAreTheUnionOfTheRolePermissions() {
        UserEntity user = user(UserState.ACTIVO, Set.of(
                TestEntities.role("BRIGADISTA", "ms05:panel:read_own", "ms01:profile:read"),
                TestEntities.role("AUDITOR", "ms08:audit:read", "ms01:profile:read")));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), CORR);

        verify(jwtService).issueToken(user.getId(), EMAIL, List.of("AUDITOR", "BRIGADISTA"),
                List.of("ms01:profile:read", "ms05:panel:read_own", "ms08:audit:read"));
    }

        /**
     * A custom role enters the token the same as a seeded one
     */
    @Test
    void login_customRolePermissionsEnterTheToken() {
        UserEntity user = user(UserState.ACTIVO, Set.of(TestEntities.role("MONITOR_EVACUACION", "ms04:checkin:write")));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), CORR);

        verify(jwtService).issueToken(user.getId(), EMAIL, List.of("MONITOR_EVACUACION"),
                List.of("ms04:checkin:write"));
    }

        /**
     * The permissions of other modules that are in the database enter the token as they were stored
     */
    @Test
    void login_permissionsOfOtherModulesEnterTheTokenAsStored() {
        UserEntity user = user(UserState.ACTIVO, Set.of(
                TestEntities.role("ESTUDIANTE", "ms01:profile:read", "ms02:campus:read", "ms07:tutor:use")));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), CORR);

        verify(jwtService).issueToken(user.getId(), EMAIL, List.of("ESTUDIANTE"),
                List.of("ms01:profile:read", "ms02:campus:read", "ms07:tutor:use"));
    }

        /**
     * A permission repeated in several roles appears only once in the token
     */
    @Test
    void login_permissionRepeatedInSeveralRoles_appearsOnceInTheToken() {
        UserEntity user = user(UserState.ACTIVO, Set.of(
                TestEntities.role("A", "ms01:profile:read", "ms04:checkin:write"),
                TestEntities.role("B", "ms01:profile:read", "ms04:checkin:write")));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), CORR);

        verify(jwtService).issueToken(user.getId(), EMAIL, List.of("A", "B"),
                List.of("ms01:profile:read", "ms04:checkin:write"));
    }

    // ---------------------------------------------------------------- A1: credenciales incorrectas

        /**
     * An unknown email produces incorrect credentials and the failure is audited
     */
    @Test
    void login_unknownEmail_throwsInvalidCredentialsAndAuditsFailure() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        AuditEvent event = publishedEvent();
        assertEquals(AuditAction.LOGIN, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertNull(event.usuarioId());
        assertNull(event.recursoAfectado());
        assertEquals("CREDENCIALES_INCORRECTAS", event.detalle());
        assertEquals(CORR, event.correlationId());
        verifyNoInteractions(jwtService);
    }

        /**
     * An unknown email also compares against a padding hash to equalize the response times
     */
    @Test
    void login_unknownEmail_stillComparesAgainstDummyHashToEqualizeResponseTime() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
    }

        /**
     * An incorrect password produces incorrect credentials and the failure is audited
     */
    @Test
    void login_wrongPassword_throwsInvalidCredentialsAndAuditsFailure() {
        UserEntity user = user(UserState.ACTIVO, "ESTUDIANTE");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(user.getId(), event.usuarioId());
        assertEquals("CREDENCIALES_INCORRECTAS", event.detalle());
        verifyNoInteractions(jwtService);
    }

        /**
     * An unknown email and an incorrect password produce the same error
     */
    @Test
    void login_unknownEmailAndWrongPassword_areIndistinguishable() {
        UserEntity user = user(UserState.ACTIVO, "ESTUDIANTE");
        when(userRepository.findByEmail("nadie@escuelaing.edu.co")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        InvalidCredentialsException unknownEmail = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(request("nadie@escuelaing.edu.co", PASSWORD), CORR));
        InvalidCredentialsException wrongPassword = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(request(EMAIL, "Incorrecta#1"), CORR_2));

        assertSame(unknownEmail.getClass(), wrongPassword.getClass());
        assertEquals(unknownEmail.getMessage(), wrongPassword.getMessage());
        assertEquals("Credenciales incorrectas.", wrongPassword.getMessage());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(2)).publish(captor.capture());
        assertEquals(captor.getAllValues().get(0).detalle(), captor.getAllValues().get(1).detalle());
    }

    // ---------------------------------------------------------------- A2: cuenta inactiva

        /**
     * An inactive account with the correct password produces inactive account and the failure is audited
     */
    @Test
    void login_inactiveAccountWithCorrectPassword_throwsInactiveAccountAndAuditsFailure() {
        UserEntity user = user(UserState.INACTIVO, "ESTUDIANTE");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);

        InactiveAccountException ex = assertThrows(InactiveAccountException.class,
                () -> authService.login(request(EMAIL, PASSWORD), CORR));

        assertEquals("Cuenta inactiva. Contacte al administrador.", ex.getMessage());
        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(user.getId(), event.usuarioId());
        assertEquals("CUENTA_INACTIVA", event.detalle());
        verifyNoInteractions(jwtService);
    }

        /**
     * An inactive account with an incorrect password does not reveal the status of the account
     */
    @Test
    void login_inactiveAccountWithWrongPassword_doesNotRevealTheAccountState() {
        UserEntity user = user(UserState.INACTIVO, "ESTUDIANTE");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        assertEquals("CREDENCIALES_INCORRECTAS", publishedEvent().detalle());
    }

        /**
     * An account without status is treated as inactive
     */
    @Test
    void login_accountWithoutState_isTreatedAsInactive() {
        UserEntity user = user(null, "ESTUDIANTE");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);

        assertThrows(InactiveAccountException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        verifyNoInteractions(jwtService);
    }

    // ---------------------------------------------------------------- internal errors and audit

        /**
     * If the issuance of the token fails the failure is audited and the error is propagated
     */
    @Test
    void login_tokenIssuanceFails_auditsFailureAndPropagatesTheError() {
        UserEntity user = user(UserState.ACTIVO, "ESTUDIANTE");
        IllegalStateException failure = new IllegalStateException("The JWT could not be signed.");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> authService.login(request(EMAIL, PASSWORD), CORR));

        assertSame(failure, thrown);
        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("ERROR_INTERNO", event.detalle());
    }

        /**
     * A failure of the audit does not change the response of the login
     */
    @Test
    void login_auditFailureDoesNotChangeTheResponse() {
        UserEntity user = user(UserState.ACTIVO, "ESTUDIANTE");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));
        doThrow(new RuntimeException("MS08 down")).when(auditPublisher).publish(any(AuditEvent.class));

        LoginResponse response = authService.login(request(EMAIL, PASSWORD), CORR);

        assertEquals(TOKEN, response.token());
    }

        /**
     * A failure of the audit does not hide the authentication error
     */
    @Test
    void login_auditFailureDoesNotHideTheAuthenticationError() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("MS08 down")).when(auditPublisher).publish(any(AuditEvent.class));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));
    }

        /**
     * Every login attempt is audited exactly once
     */
    @Test
    void login_everyAttemptIsAuditedExactlyOnce() {
        UserEntity user = user(UserState.ACTIVO, "ESTUDIANTE");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), CORR);

        verify(auditPublisher, times(1)).publish(any(AuditEvent.class));
    }

        /**
     * The audit event never includes the password, its hash or the token
     */
    @Test
    void login_auditEventNeverCarriesPasswordHashOrToken() {
        UserEntity user = user(UserState.ACTIVO, "ESTUDIANTE");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any(), any()))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), CORR);

        String printed = publishedEvent().toString();
        assertFalse(printed.contains(PASSWORD));
        assertFalse(printed.contains(STORED_HASH));
        assertFalse(printed.contains(TOKEN));
    }

    // ---------------------------------------------------------------- validation of form F-02

        /**
     * An email with an invalid format is rejected with the message of the form before querying the database
     */
    @Test
    void login_invalidEmail_isRejectedWithTheFormMessageBeforeQueryingTheDatabase() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> authService.login(request("no-es-un-correo", PASSWORD), CORR));

        assertEquals(1, error.getErrors().size());
        assertEquals("correo", error.getErrors().get(0).campo());
        assertEquals("Ingrese un correo válido.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(jwtService);
    }

        /**
     * A blank password is rejected with the message of the form
     */
    @Test
    void login_blankPassword_isRejectedWithTheFormMessage() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> authService.login(request(EMAIL, "   "), CORR));

        assertEquals("contrasena", error.getErrors().get(0).campo());
        assertEquals("Ingrese su contraseña.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(userRepository);
    }

        /**
     * If both fields are missing both are reported in a stable order
     */
    @Test
    void login_missingBothFields_reportsBothFieldsInStableOrder() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> authService.login(new LoginRequest(), CORR));

        assertEquals(2, error.getErrors().size());
        assertEquals("contrasena", error.getErrors().get(0).campo());
        assertEquals("correo", error.getErrors().get(1).campo());
        assertEquals("Datos inválidos.", error.getMessage());
    }

        /**
     * A password of more than 64 characters is rejected
     */
    @Test
    void login_passwordLongerThan64Characters_isRejected() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> authService.login(request(EMAIL, "a".repeat(65)), CORR));

        assertEquals("La contraseña no puede superar los 64 caracteres.", error.getErrors().get(0).mensaje());
    }

        /**
     * A field that is not in the contract is rejected (RG-04)
     */
    @Test
    void login_fieldNotDefinedInTheContract_isRejected() {
        LoginRequest request = request(EMAIL, PASSWORD);
        request.registerUnknownField("rol", "ADMIN");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> authService.login(request, CORR));

        assertEquals("rol", error.getErrors().get(0).campo());
        verifyNoInteractions(userRepository);
    }

        /**
     * Malformed requests are not audited as authentication attempts
     */
    @Test
    void login_malformedRequests_areNotAuditedAsAuthenticationAttempts() {
        assertThrows(InvalidRequestException.class, () -> authService.login(new LoginRequest(), CORR));

        verifyNoInteractions(auditPublisher);
    }

    // ---------------------------------------------------------------- Correlation ID

        /**
     * Without a correlation id one is generated for the logs and the audit
     */
    @Test
    void login_missingCorrelationId_generatesOneForLogsAndAudit() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), null));

        String generated = publishedEvent().correlationId();
        assertNotNull(generated);
        assertFalse(generated.isBlank());
    }

        /**
     * A correlation id that is not a UUID v4 is replaced by a generated one
     */
    @Test
    void login_unsafeCorrelationId_isReplacedByAGeneratedOne() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        String forged = "abc\r\nINJECTED log line";

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), forged));

        String used = publishedEvent().correlationId();
        assertFalse(used.contains("INJECTED"));
        assertTrue(used.matches("[0-9a-f-]{36}"));
    }

        /**
     * The login never writes to the user repository
     */
    @Test
    void login_userRepositoryIsNeverWrittenTo() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        verify(userRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- transaction

        /**
     * The login runs in a read-only transaction
     *
     * @throws NoSuchMethodException if the method of the service is not found
     */
    @Test
    void login_runsInAReadOnlyTransaction() throws NoSuchMethodException {
        Transactional transactional = AuthServiceImpl.class
                .getMethod("login", LoginRequest.class, String.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
    }
}
