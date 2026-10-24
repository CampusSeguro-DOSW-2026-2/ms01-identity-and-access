package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PasswordResetCodeEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidResetCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.PasswordMismatchException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.SameAsCurrentPasswordException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.WrongCurrentPasswordException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PasswordResetCodeRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.EmailService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PasswordServiceImpl;

/**
 * RF-MS01-08: flujo A (restablecer contrasena olvidada), flujo B (cambio autenticado), reglas
 * RN-MS01-20 a RN-MS01-22 y auditoria RECUPERACION / CAMBIO_CONTRASENA.
 */
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");
    private static final String EMAIL = "maria.perez@mail.escuelaing.edu.co";
    private static final String STORED_HASH = "$2a$10$stored.hash.value";
    private static final String CODE_HASH = "$2a$10$code.hash.value";
    private static final String CODE = "123456";
    private static final String CURRENT = "Actual#2026";
    private static final String NEW_PASSWORD = "Nueva#2026x";
    private static final String NEW_HASH = "new-hash";
    private static final int VALID_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;
    private static final int COOLDOWN_SECONDS = 60;
    private static final String GENERIC_MESSAGE = "Si el correo está registrado, enviamos un código de recuperación.";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetCodeRepository codeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditEventPublisher auditPublisher;

    private PasswordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PasswordServiceImpl(userRepository, codeRepository, passwordEncoder, emailService,
                auditPublisher, Clock.fixed(NOW, ZoneOffset.UTC), VALID_MINUTES, MAX_ATTEMPTS, COOLDOWN_SECONDS);
    }

    // ---------------------------------------------------------------- utilidades

    private UserEntity user(UserState state) {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setFirstName("Maria");
        entity.setFirstLastName("Perez");
        entity.setEmail(EMAIL);
        entity.setPassword(STORED_HASH);
        entity.setRoles(List.of(UserRole.ESTUDIANTE));
        entity.setState(state);
        return entity;
    }

    private PasswordResetCodeEntity activeCode(UserEntity user, int attempts, Instant expiresAt) {
        PasswordResetCodeEntity code = new PasswordResetCodeEntity();
        code.setUser(user);
        code.setCodeHash(CODE_HASH);
        code.setAttempts(attempts);
        code.setExpiresAt(expiresAt);
        return code;
    }

    /** Codigo previo cuya fecha de creacion se controla (la entidad real usa Instant.now()). */
    private PasswordResetCodeEntity previousCodeCreatedAt(Instant createdAt) {
        PasswordResetCodeEntity last = mock(PasswordResetCodeEntity.class);
        when(last.getCreatedAt()).thenReturn(createdAt);
        return last;
    }

    private ForgotPasswordRequest forgot(String email) {
        return new ForgotPasswordRequest(email);
    }

    private ResetPasswordRequest reset(String email, String code, String password, String confirm) {
        return new ResetPasswordRequest(email, code, password, confirm);
    }

    private ChangePasswordRequest change(String current, String password, String confirm) {
        return new ChangePasswordRequest(current, password, confirm);
    }

    private AuditEvent publishedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------- flujo A: solicitar codigo

    @Test
    void forgot_activeUserWithoutPreviousCode_storesHashAndSendsEmail() {
        UserEntity user = user(UserState.ACTIVO);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());
        when(codeRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn(CODE_HASH);

        MessageResponse response = service.requestResetCode(forgot(EMAIL), "corr-1");

        assertEquals(GENERIC_MESSAGE, response.mensaje());

        ArgumentCaptor<PasswordResetCodeEntity> saved = ArgumentCaptor.forClass(PasswordResetCodeEntity.class);
        verify(codeRepository).save(saved.capture());
        assertEquals(CODE_HASH, saved.getValue().getCodeHash());
        assertEquals(user, saved.getValue().getUser());
        assertEquals(NOW.plusSeconds(VALID_MINUTES * 60L), saved.getValue().getExpiresAt());

        ArgumentCaptor<String> sentCode = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendResetCode(eq(EMAIL), sentCode.capture(), eq(VALID_MINUTES));
        assertTrue(sentCode.getValue().matches("\\d{6}"), "el codigo debe ser de 6 digitos");
        // Lo que se guarda es el hash del mismo codigo que se envia, nunca el codigo en claro.
        verify(passwordEncoder).encode(sentCode.getValue());
    }

    @Test
    void forgot_emailIsTrimmedAndLowercased() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        MessageResponse response = service.requestResetCode(forgot("  Maria.Perez@Mail.Escuelaing.edu.co "), null);

        assertEquals(GENERIC_MESSAGE, response.mensaje());
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void forgot_unknownEmail_returnsSameMessageAndSendsNothing() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        MessageResponse response = service.requestResetCode(forgot(EMAIL), "corr-1");

        assertEquals(GENERIC_MESSAGE, response.mensaje());
        verifyNoInteractions(emailService);
        verify(codeRepository, never()).save(any());
    }

    @Test
    void forgot_inactiveUser_returnsSameMessageAndSendsNothing() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserState.INACTIVO)));

        MessageResponse response = service.requestResetCode(forgot(EMAIL), "corr-1");

        assertEquals(GENERIC_MESSAGE, response.mensaje());
        verifyNoInteractions(emailService);
        verify(codeRepository, never()).save(any());
    }

    @Test
    void forgot_withinCooldown_doesNotIssueNewCode() {
        UserEntity user = user(UserState.ACTIVO);
        PasswordResetCodeEntity previous = previousCodeCreatedAt(NOW.minusSeconds(30));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(previous));

        MessageResponse response = service.requestResetCode(forgot(EMAIL), "corr-1");

        assertEquals(GENERIC_MESSAGE, response.mensaje());
        verifyNoInteractions(emailService);
        verify(codeRepository, never()).save(any());
    }

    @Test
    void forgot_afterCooldown_issuesNewCode() {
        UserEntity user = user(UserState.ACTIVO);
        PasswordResetCodeEntity previous = previousCodeCreatedAt(NOW.minusSeconds(COOLDOWN_SECONDS + 1));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(previous));
        when(codeRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn(CODE_HASH);

        service.requestResetCode(forgot(EMAIL), "corr-1");

        verify(emailService).sendResetCode(eq(EMAIL), anyString(), eq(VALID_MINUTES));
    }

    @Test
    void forgot_pendingCodesAreInvalidatedWhenNewOneIsIssued() {
        UserEntity user = user(UserState.ACTIVO);
        PasswordResetCodeEntity old = activeCode(user, 0, NOW.plusSeconds(300));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());
        when(codeRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of(old));
        when(passwordEncoder.encode(anyString())).thenReturn(CODE_HASH);

        service.requestResetCode(forgot(EMAIL), "corr-1");

        assertEquals(NOW, old.getUsedAt());
        verify(codeRepository).saveAll(List.of(old));
    }

    @Test
    void forgot_mailFailure_doesNotChangeTheResponse() {
        UserEntity user = user(UserState.ACTIVO);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());
        when(codeRepository.findByUserAndUsedAtIsNull(user)).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn(CODE_HASH);
        doThrow(new MailSendException("smtp caido")).when(emailService).sendResetCode(anyString(), anyString(), anyInt());

        MessageResponse response = service.requestResetCode(forgot(EMAIL), "corr-1");

        assertEquals(GENERIC_MESSAGE, response.mensaje());
    }

    // ------------------------------------------------ flujo A: restablecer

    @Test
    void reset_validCode_changesPasswordMarksCodeUsedAndAuditsSuccess() {
        UserEntity user = user(UserState.ACTIVO);
        PasswordResetCodeEntity code = activeCode(user, 2, NOW.plusSeconds(300));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)).thenReturn(Optional.of(code));
        when(passwordEncoder.matches(CODE, CODE_HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

        MessageResponse response = service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD), "corr-1");

        assertEquals("Contraseña actualizada correctamente. Inicia sesión de nuevo.", response.mensaje());
        assertEquals(NEW_HASH, user.getPassword());
        assertEquals(NOW, code.getUsedAt());
        verify(userRepository).save(user);
        verify(codeRepository).save(code);

        AuditEvent event = publishedEvent();
        assertEquals(AuditAction.RECUPERACION, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(user.getId(), event.usuarioId());
        assertEquals(EMAIL, event.correo());
        assertNull(event.detalle());
        assertEquals("corr-1", event.correlationId());
        assertEquals(NOW, event.fecha());
    }

    @Test
    void reset_confirmationDoesNotMatch_throwsAndAuditsWithoutTouchingData() {
        assertThrows(PasswordMismatchException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, "Otra#2026xx"), "corr-1"));

        verifyNoInteractions(userRepository);
        verifyNoInteractions(codeRepository);

        AuditEvent event = publishedEvent();
        assertEquals(AuditAction.RECUPERACION, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CONFIRMACION_DISTINTA", event.detalle());
        assertNull(event.usuarioId());
    }

    @Test
    void reset_unknownEmail_throwsGenericErrorAndAuditsFailure() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD), "corr-1"));

        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CODIGO_INVALIDO", event.detalle());
        assertNull(event.usuarioId());
    }

    @Test
    void reset_inactiveUser_throwsGenericError() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserState.INACTIVO)));

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD), "corr-1"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void reset_noActiveCode_throwsGenericError() {
        UserEntity user = user(UserState.ACTIVO);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD), "corr-1"));

        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(user.getId(), event.usuarioId());
    }

    @Test
    void reset_expiredCode_throwsGenericErrorWithoutCheckingTheCode() {
        UserEntity user = user(UserState.ACTIVO);
        PasswordResetCodeEntity expired = activeCode(user, 0, NOW.minusSeconds(1));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)).thenReturn(Optional.of(expired));

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD), "corr-1"));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void reset_attemptsExhausted_throwsEvenWithTheRightCode() {
        UserEntity user = user(UserState.ACTIVO);
        PasswordResetCodeEntity blocked = activeCode(user, MAX_ATTEMPTS, NOW.plusSeconds(300));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)).thenReturn(Optional.of(blocked));

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD), "corr-1"));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void reset_wrongCode_incrementsAttemptsAndAuditsFailure() {
        UserEntity user = user(UserState.ACTIVO);
        PasswordResetCodeEntity code = activeCode(user, 1, NOW.plusSeconds(300));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)).thenReturn(Optional.of(code));
        when(passwordEncoder.matches("000000", CODE_HASH)).thenReturn(false);

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, "000000", NEW_PASSWORD, NEW_PASSWORD), "corr-1"));

        assertEquals(2, code.getAttempts());
        assertNull(code.getUsedAt());
        verify(codeRepository).save(code);
        verify(userRepository, never()).save(any());

        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CODIGO_INVALIDO", event.detalle());
        assertEquals(user.getId(), event.usuarioId());
    }

    @Test
    void reset_auditFailure_doesNotBreakTheOperation() {
        UserEntity user = user(UserState.ACTIVO);
        PasswordResetCodeEntity code = activeCode(user, 0, NOW.plusSeconds(300));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)).thenReturn(Optional.of(code));
        when(passwordEncoder.matches(CODE, CODE_HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);
        doThrow(new RuntimeException("MS08 caido")).when(auditPublisher).publish(any(AuditEvent.class));

        MessageResponse response = service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD), "corr-1");

        assertEquals(NEW_HASH, user.getPassword());
        assertNotNull(response.mensaje());
    }

    @Test
    void reset_missingOrUnsafeCorrelationId_generatesOne() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD), "id con espacios"));

        String generated = publishedEvent().correlationId();
        assertNotNull(generated);
        assertEquals(36, generated.length());
    }

    // ----------------------------------------- flujo B: cambio autenticado

    @Test
    void change_correctCurrentPassword_updatesHashAndAuditsSuccess() {
        UserEntity user = user(UserState.ACTIVO);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT, STORED_HASH)).thenReturn(true);
        when(passwordEncoder.matches(NEW_PASSWORD, STORED_HASH)).thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

        MessageResponse response = service.changePassword(user.getId(), change(CURRENT, NEW_PASSWORD, NEW_PASSWORD), "corr-9");

        assertEquals("Contraseña actualizada correctamente.", response.mensaje());
        assertEquals(NEW_HASH, user.getPassword());
        verify(userRepository).save(user);

        AuditEvent event = publishedEvent();
        assertEquals(AuditAction.CAMBIO_CONTRASENA, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(user.getId(), event.usuarioId());
        assertEquals(EMAIL, event.correo());
        assertNull(event.detalle());
        assertEquals("corr-9", event.correlationId());
        assertEquals(NOW, event.fecha());
    }

    @Test
    void change_unknownUser_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service.changePassword(unknown, change(CURRENT, NEW_PASSWORD, NEW_PASSWORD), "corr-9"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void change_confirmationDoesNotMatch_throwsAndAuditsFailure() {
        UserEntity user = user(UserState.ACTIVO);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(PasswordMismatchException.class,
                () -> service.changePassword(user.getId(), change(CURRENT, NEW_PASSWORD, "Otra#2026xx"), "corr-9"));

        verify(userRepository, never()).save(any());
        AuditEvent event = publishedEvent();
        assertEquals(AuditAction.CAMBIO_CONTRASENA, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CONFIRMACION_DISTINTA", event.detalle());
    }

    @Test
    void change_wrongCurrentPassword_throwsAndAuditsFailure() {
        UserEntity user = user(UserState.ACTIVO);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Incorrecta#1", STORED_HASH)).thenReturn(false);

        assertThrows(WrongCurrentPasswordException.class,
                () -> service.changePassword(user.getId(), change("Incorrecta#1", NEW_PASSWORD, NEW_PASSWORD), "corr-9"));

        assertEquals(STORED_HASH, user.getPassword());
        verify(userRepository, never()).save(any());
        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CONTRASENA_ACTUAL_INCORRECTA", event.detalle());
        assertEquals(user.getId(), event.usuarioId());
    }

    @Test
    void change_newPasswordEqualsCurrent_throwsAndAuditsFailure() {
        UserEntity user = user(UserState.ACTIVO);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT, STORED_HASH)).thenReturn(true);

        assertThrows(SameAsCurrentPasswordException.class,
                () -> service.changePassword(user.getId(), change(CURRENT, CURRENT, CURRENT), "corr-9"));

        assertEquals(STORED_HASH, user.getPassword());
        verify(userRepository, never()).save(any());
        AuditEvent event = publishedEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("NUEVA_IGUAL_A_ACTUAL", event.detalle());
    }
}