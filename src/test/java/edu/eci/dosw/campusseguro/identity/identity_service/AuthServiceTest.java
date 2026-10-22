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
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IssuedToken;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthServiceImpl;

/**
 * RF-MS01-02 / historia KAIR-16: flujo base, flujos alternos A1 y A2, RN-MS01-05, RN-MS01-06 y
 * auditoria de todos los intentos.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00Z");
    private static final String DUMMY_HASH = "dummy-hash";
    private static final String STORED_HASH = "$2a$10$stored.hash.value";
    private static final String EMAIL = "maria.perez@mail.escuelaing.edu.co";
    private static final String PASSWORD = "Campus#2026";
    private static final String TOKEN = "header.payload.signature";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditEventPublisher auditPublisher;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        // El servicio calcula un hash de relleno al construirse (igualacion de tiempos).
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, auditPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LoginRequest request(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setCorreo(email);
        request.setContrasena(password);
        return request;
    }

    private UserEntity user(UserState state, UserRole... roles) {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setFirstName("Maria");
        entity.setFirstLastName("Perez");
        entity.setEmail(EMAIL);
        entity.setPassword(STORED_HASH);
        entity.setRoles(List.of(roles));
        entity.setState(state);
        return entity;
    }

    private AuditEvent publishedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- flujo base

    @Test
    void login_activeUserWithCorrectPassword_returnsTokenAndAuditsSuccess() {
        UserEntity user = user(UserState.ACTIVO, UserRole.PROFESOR, UserRole.ESTUDIANTE);
        IssuedToken issued = new IssuedToken(TOKEN, NOW.plusSeconds(3600));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(user.getId(), EMAIL, List.of(UserRole.ESTUDIANTE, UserRole.PROFESOR)))
                .thenReturn(issued);

        LoginResponse response = authService.login(request(EMAIL, PASSWORD), "corr-1");

        assertEquals(TOKEN, response.token());
        assertEquals("Bearer", response.tipo());
        assertEquals("2026-10-22T06:00:00Z", response.expiraEn());
        assertEquals(user.getId(), response.usuario().id());
        assertEquals(EMAIL, response.usuario().correo());
        assertEquals(List.of(UserRole.ESTUDIANTE, UserRole.PROFESOR), response.usuario().roles());

        AuditEvent event = publishedEvent();
        assertEquals(AuditAction.LOGIN, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(user.getId(), event.usuarioId());
        assertEquals(EMAIL, event.correo());
        assertNull(event.detalle());
        assertEquals("corr-1", event.correlationId());
        assertEquals(NOW, event.fecha());
    }

    @Test
    void login_rolesAreSortedAlphabeticallyInTokenAndResponse() {
        UserEntity user = user(UserState.ACTIVO, UserRole.PROFESOR, UserRole.ADMIN, UserRole.BRIGADISTA);
        List<UserRole> alphabetical = List.of(UserRole.ADMIN, UserRole.BRIGADISTA, UserRole.PROFESOR);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(user.getId(), EMAIL, alphabetical))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        LoginResponse response = authService.login(request(EMAIL, PASSWORD), "corr-1");

        assertEquals(alphabetical, response.usuario().roles());
    }

    @Test
    void login_emailIsNormalizedBeforeLookup() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request("  Maria.Perez@Mail.EscuelaIng.edu.co ", PASSWORD), "corr-1");

        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void login_passwordIsNeverTrimmedOrNormalized() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(" Campus#2026 ", STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, " Campus#2026 "), "corr-1");

        verify(passwordEncoder).matches(" Campus#2026 ", STORED_HASH);
    }

    @Test
    void login_verifiesAgainstRealBCryptHashLikeTheOneStoredAtRegistration() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        AuthServiceImpl service = new AuthServiceImpl(userRepository, bcrypt, jwtService, auditPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        user.setPassword(bcrypt.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        assertEquals(TOKEN, service.login(request(EMAIL, PASSWORD), "corr-1").token());
        assertThrows(InvalidCredentialsException.class, () -> service.login(request(EMAIL, "Otra#2026x"), "corr-2"));
    }

    // ---------------------------------------------------------------- A1: credenciales incorrectas

    @Test
    void login_unknownEmail_throwsInvalidCredentialsAndAuditsFailure() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), "corr-1"));

        AuditEvent event = publishedEvent();
        assertEquals(AuditAction.LOGIN, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertNull(event.usuarioId());
        assertEquals(EMAIL, event.correo());
        assertEquals("CREDENCIALES_INCORRECTAS", event.detalle());
        assertEquals("corr-1", event.correlationId());
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_unknownEmail_stillComparesAgainstDummyHashToEqualizeResponseTime() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), "corr-1"));

        verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsAndAuditsFailure() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), "corr-1"));

        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(user.getId(), event.usuarioId());
        assertEquals("CREDENCIALES_INCORRECTAS", event.detalle());
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_unknownEmailAndWrongPassword_areIndistinguishable() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail("nadie@mail.escuelaing.edu.co")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        InvalidCredentialsException unknownEmail = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(request("nadie@mail.escuelaing.edu.co", PASSWORD), "corr-1"));
        InvalidCredentialsException wrongPassword = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(request(EMAIL, "Incorrecta#1"), "corr-2"));

        assertSame(unknownEmail.getClass(), wrongPassword.getClass());
        assertEquals(unknownEmail.getMessage(), wrongPassword.getMessage());
        assertEquals("Credenciales incorrectas.", wrongPassword.getMessage());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, times(2)).publish(captor.capture());
        assertEquals(captor.getAllValues().get(0).detalle(), captor.getAllValues().get(1).detalle());
    }

    // ---------------------------------------------------------------- A2: cuenta inactiva

    @Test
    void login_inactiveAccountWithCorrectPassword_throwsInactiveAccountAndAuditsFailure() {
        UserEntity user = user(UserState.INACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);

        InactiveAccountException ex = assertThrows(InactiveAccountException.class,
                () -> authService.login(request(EMAIL, PASSWORD), "corr-1"));

        assertEquals("Cuenta inactiva. Contacte al administrador.", ex.getMessage());
        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(user.getId(), event.usuarioId());
        assertEquals("CUENTA_INACTIVA", event.detalle());
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_inactiveAccountWithWrongPassword_doesNotRevealTheAccountState() {
        UserEntity user = user(UserState.INACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), "corr-1"));

        assertEquals("CREDENCIALES_INCORRECTAS", publishedEvent().detalle());
    }

    @Test
    void login_accountWithoutState_isTreatedAsInactive() {
        UserEntity user = user(null, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);

        assertThrows(InactiveAccountException.class, () -> authService.login(request(EMAIL, PASSWORD), "corr-1"));

        verifyNoInteractions(jwtService);
    }

    // ---------------------------------------------------------------- errores internos y auditoria

    @Test
    void login_tokenIssuanceFails_auditsFailureAndPropagatesTheError() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        IllegalStateException failure = new IllegalStateException("No fue posible firmar el JWT.");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> authService.login(request(EMAIL, PASSWORD), "corr-1"));

        assertSame(failure, thrown);
        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("ERROR_INTERNO", event.detalle());
    }

    @Test
    void login_auditFailureDoesNotChangeTheResponse() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));
        doThrow(new RuntimeException("MS08 caido")).when(auditPublisher).publish(any(AuditEvent.class));

        LoginResponse response = authService.login(request(EMAIL, PASSWORD), "corr-1");

        assertEquals(TOKEN, response.token());
    }

    @Test
    void login_auditFailureDoesNotHideTheAuthenticationError() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("MS08 caido")).when(auditPublisher).publish(any(AuditEvent.class));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), "corr-1"));
    }

    @Test
    void login_everyAttemptIsAuditedExactlyOnce() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), "corr-1");

        verify(auditPublisher, times(1)).publish(any(AuditEvent.class));
    }

    @Test
    void login_auditEventNeverCarriesPasswordHashOrToken() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), "corr-1");

        String printed = publishedEvent().toString();
        assertFalse(printed.contains(PASSWORD));
        assertFalse(printed.contains(STORED_HASH));
        assertFalse(printed.contains(TOKEN));
    }

    // ---------------------------------------------------------------- Correlation ID

    @Test
    void login_missingCorrelationId_generatesOneForLogsAndAudit() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), null));

        String generated = publishedEvent().correlationId();
        assertNotNull(generated);
        assertFalse(generated.isBlank());
    }

    @Test
    void login_unsafeCorrelationId_isReplacedByAGeneratedOne() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        String forged = "abc\r\nINJECTED log line";

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), forged));

        String used = publishedEvent().correlationId();
        assertFalse(used.contains("INJECTED"));
        assertTrue(used.matches("[0-9a-f-]{36}"));
    }

    @Test
    void login_userRepositoryIsNeverWrittenTo() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), "corr-1"));

        verify(userRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- transaccion

    @Test
    void login_runsInAReadOnlyTransaction() throws NoSuchMethodException {
        Transactional transactional = AuthServiceImpl.class
                .getMethod("login", LoginRequest.class, String.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
    }
}
