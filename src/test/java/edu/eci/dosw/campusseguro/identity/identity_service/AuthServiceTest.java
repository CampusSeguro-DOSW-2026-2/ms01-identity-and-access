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
import java.util.HashSet;
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
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
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
 * RF-MS01-02: flujo base, flujos alternos A1 y A2, RN-MS01-05, RN-MS01-06 y auditoría de todos los
 * intentos
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
     * Prepara el servicio de autenticación con sus dependencias simuladas
     */
    @BeforeEach
    void setUp() {
        // El servicio calcula un hash de relleno al construirse (igualacion de tiempos).
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, recorder(),
                requestValidator());
    }

    /**
     * Crea el registrador de auditoría que usa el servicio bajo prueba
     *
     * @return el registrador de auditoría
     */
    private AuditRecorder recorder() {
        return new AuditRecorder(auditPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * Crea el validador de peticiones que usa el servicio bajo prueba
     *
     * @return el validador de peticiones
     */
    private RequestValidator requestValidator() {
        return new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator());
    }

    /**
     * Construye una petición de login
     *
     * @param email correo de la petición
     * @param password contraseña de la petición
     * @return la petición de login
     */
    private LoginRequest request(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setCorreo(email);
        request.setContrasena(password);
        return request;
    }

    /**
     * Construye un usuario de prueba con la contraseña esperada
     *
     * @param state estado del usuario
     * @param roles roles del usuario
     * @return el usuario de prueba
     */
    private UserEntity user(UserState state, UserRole... roles) {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setFirstName("Maria");
        entity.setFirstLastName("Perez");
        entity.setEmail(EMAIL);
        entity.setPassword(STORED_HASH);
        entity.setRoles(new HashSet<>(List.of(roles)));
        entity.setState(state);
        return entity;
    }

    /**
     * Obtiene el único evento de auditoría que se publicó
     *
     * @return el evento de auditoría publicado
     */
    private AuditEvent publishedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- flujo base

    /**
     * Un usuario activo con la contraseña correcta recibe el token y se audita el éxito
     */
    @Test
    void login_activeUserWithCorrectPassword_returnsTokenAndAuditsSuccess() {
        UserEntity user = user(UserState.ACTIVO, UserRole.PROFESOR, UserRole.ESTUDIANTE);
        IssuedToken issued = new IssuedToken(TOKEN, NOW.plusSeconds(3600));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(user.getId(), EMAIL, List.of(UserRole.ESTUDIANTE, UserRole.PROFESOR)))
                .thenReturn(issued);

        LoginResponse response = authService.login(request(EMAIL, PASSWORD), CORR);

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
        assertEquals("usuario:" + user.getId(), event.recursoAfectado());
        assertNull(event.detalle());
        assertEquals(CORR, event.correlationId());
        assertEquals(NOW, event.fecha());
    }

    /**
     * Los roles se ordenan alfabéticamente en el token y en la respuesta
     */
    @Test
    void login_rolesAreSortedAlphabeticallyInTokenAndResponse() {
        UserEntity user = user(UserState.ACTIVO, UserRole.PROFESOR, UserRole.ADMIN, UserRole.BRIGADISTA);
        List<UserRole> alphabetical = List.of(UserRole.ADMIN, UserRole.BRIGADISTA, UserRole.PROFESOR);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(user.getId(), EMAIL, alphabetical))
                .thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        LoginResponse response = authService.login(request(EMAIL, PASSWORD), CORR);

        assertEquals(alphabetical, response.usuario().roles());
    }

    /**
     * El correo se normaliza antes de consultar al usuario
     */
    @Test
    void login_emailIsNormalizedBeforeLookup() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request("  Maria.Perez@EscuelaIng.edu.co ", PASSWORD), CORR);

        verify(userRepository).findByEmail(EMAIL);
    }

    /**
     * La contraseña nunca se recorta ni se normaliza
     */
    @Test
    void login_passwordIsNeverTrimmedOrNormalized() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(" Campus#2026 ", STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, " Campus#2026 "), CORR);

        verify(passwordEncoder).matches(" Campus#2026 ", STORED_HASH);
    }

    /**
     * La verificación funciona con un hash BCrypt real como el que se guarda en el registro
     */
    @Test
    void login_verifiesAgainstRealBCryptHashLikeTheOneStoredAtRegistration() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        AuthServiceImpl service = new AuthServiceImpl(userRepository, bcrypt, jwtService, recorder(),
                requestValidator());
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        user.setPassword(bcrypt.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        assertEquals(TOKEN, service.login(request(EMAIL, PASSWORD), CORR).token());
        assertThrows(InvalidCredentialsException.class, () -> service.login(request(EMAIL, "Otra#2026x"), CORR_2));
    }

    // ---------------------------------------------------------------- A1: credenciales incorrectas

    /**
     * Un correo desconocido produce credenciales incorrectas y se audita el fallo
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
     * Un correo desconocido también compara contra un hash de relleno para igualar los tiempos de respuesta
     */
    @Test
    void login_unknownEmail_stillComparesAgainstDummyHashToEqualizeResponseTime() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
    }

    /**
     * Una contraseña incorrecta produce credenciales incorrectas y se audita el fallo
     */
    @Test
    void login_wrongPassword_throwsInvalidCredentialsAndAuditsFailure() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
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
     * Un correo desconocido y una contraseña incorrecta producen el mismo error
     */
    @Test
    void login_unknownEmailAndWrongPassword_areIndistinguishable() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
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
     * Una cuenta inactiva con la contraseña correcta produce cuenta inactiva y se audita el fallo
     */
    @Test
    void login_inactiveAccountWithCorrectPassword_throwsInactiveAccountAndAuditsFailure() {
        UserEntity user = user(UserState.INACTIVO, UserRole.ESTUDIANTE);
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
     * Una cuenta inactiva con contraseña incorrecta no revela el estado de la cuenta
     */
    @Test
    void login_inactiveAccountWithWrongPassword_doesNotRevealTheAccountState() {
        UserEntity user = user(UserState.INACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        assertEquals("CREDENCIALES_INCORRECTAS", publishedEvent().detalle());
    }

    /**
     * Una cuenta sin estado se trata como inactiva
     */
    @Test
    void login_accountWithoutState_isTreatedAsInactive() {
        UserEntity user = user(null, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);

        assertThrows(InactiveAccountException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        verifyNoInteractions(jwtService);
    }

    // ---------------------------------------------------------------- errores internos y auditoria

    /**
     * Si falla la emisión del token se audita el fallo y se propaga el error
     */
    @Test
    void login_tokenIssuanceFails_auditsFailureAndPropagatesTheError() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        IllegalStateException failure = new IllegalStateException("No fue posible firmar el JWT.");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> authService.login(request(EMAIL, PASSWORD), CORR));

        assertSame(failure, thrown);
        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("ERROR_INTERNO", event.detalle());
    }

    /**
     * Un fallo de la auditoría no cambia la respuesta del login
     */
    @Test
    void login_auditFailureDoesNotChangeTheResponse() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));
        doThrow(new RuntimeException("MS08 caido")).when(auditPublisher).publish(any(AuditEvent.class));

        LoginResponse response = authService.login(request(EMAIL, PASSWORD), CORR);

        assertEquals(TOKEN, response.token());
    }

    /**
     * Un fallo de la auditoría no oculta el error de autenticación
     */
    @Test
    void login_auditFailureDoesNotHideTheAuthenticationError() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("MS08 caido")).when(auditPublisher).publish(any(AuditEvent.class));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));
    }

    /**
     * Cada intento de login se audita exactamente una vez
     */
    @Test
    void login_everyAttemptIsAuditedExactlyOnce() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), CORR);

        verify(auditPublisher, times(1)).publish(any(AuditEvent.class));
    }

    /**
     * El evento de auditoría nunca incluye la contraseña, su hash ni el token
     */
    @Test
    void login_auditEventNeverCarriesPasswordHashOrToken() {
        UserEntity user = user(UserState.ACTIVO, UserRole.ESTUDIANTE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(any(), any(), any())).thenReturn(new IssuedToken(TOKEN, NOW.plusSeconds(3600)));

        authService.login(request(EMAIL, PASSWORD), CORR);

        String printed = publishedEvent().toString();
        assertFalse(printed.contains(PASSWORD));
        assertFalse(printed.contains(STORED_HASH));
        assertFalse(printed.contains(TOKEN));
    }

    // ---------------------------------------------------------------- validacion del formulario F-02

    /**
     * Un correo con formato inválido se rechaza con el mensaje del formulario antes de consultar la base de datos
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
     * Una contraseña en blanco se rechaza con el mensaje del formulario
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
     * Si faltan los dos campos se informan ambos en un orden estable
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
     * Una contraseña de más de 64 caracteres se rechaza
     */
    @Test
    void login_passwordLongerThan64Characters_isRejected() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> authService.login(request(EMAIL, "a".repeat(65)), CORR));

        assertEquals("La contraseña no puede superar los 64 caracteres.", error.getErrors().get(0).mensaje());
    }

    /**
     * Un campo que no está en el contrato se rechaza (RG-04)
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
     * Las peticiones mal formadas no se auditan como intentos de autenticación
     */
    @Test
    void login_malformedRequests_areNotAuditedAsAuthenticationAttempts() {
        assertThrows(InvalidRequestException.class, () -> authService.login(new LoginRequest(), CORR));

        verifyNoInteractions(auditPublisher);
    }

    // ---------------------------------------------------------------- Correlation ID

    /**
     * Sin correlation id se genera uno para los logs y la auditoría
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
     * Un correlation id que no es un UUID v4 se reemplaza por uno generado
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
     * El login nunca escribe en el repositorio de usuarios
     */
    @Test
    void login_userRepositoryIsNeverWrittenTo() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request(EMAIL, PASSWORD), CORR));

        verify(userRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- transaccion

    /**
     * El login se ejecuta en una transacción de solo lectura
     *
     * @throws NoSuchMethodException si no se encuentra el método del servicio
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
