package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.config.RecoveryPolicy;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PasswordResetCodeEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidResetCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PasswordResetCodeRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.EmailService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PasswordServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * RF-MS01-08: recuperación de la contraseña olvidada (flujo base A) y cambio de contraseña autenticado (flujo
 * base B), flujos alternos A1, A2 y A3, RN-MS01-20, RN-MS01-21, RN-MS01-22 y auditoría de cada intento
 */
class PasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T15:00:00Z");
    private static final String CORR = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";
    private static final String EMAIL = "maria.perez@escuelaing.edu.co";
    private static final String OLD_PASSWORD = "Campus#2026";
    private static final String NEW_PASSWORD = "Nueva#2026x";
    private static final String CODE = "482913";
    private static final RecoveryPolicy POLICY = new RecoveryPolicy(15, 5, 60);

    private UserRepository userRepository;
    private PasswordResetCodeRepository codeRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private AuditEventPublisher auditPublisher;
    private PasswordServiceImpl service;
    private UUID userId;
    private UserEntity user;

    /**
     * Prepara el servicio con repositorios simulados, un codificador BCrypt real de bajo costo, un reloj fijo y
     * un ejecutor que envía los correos en el mismo hilo
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        codeRepository = mock(PasswordResetCodeRepository.class);
        emailService = mock(EmailService.class);
        auditPublisher = mock(AuditEventPublisher.class);
        passwordEncoder = spy(new BCryptPasswordEncoder(4));
        service = serviceWith(POLICY, Runnable::run);
        userId = UUID.randomUUID();
        user = user(userId, EMAIL, OLD_PASSWORD, UserState.ACTIVO);
        clearInvocations(passwordEncoder);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(codeRepository.saveAndFlush(any(PasswordResetCodeEntity.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    /**
     * Deja limpia la sincronización de transacciones y el contexto de logs que algunas pruebas activan
     */
    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        MDC.clear();
    }

    /**
     * Construye el servicio con la política y el ejecutor indicados, y deja sin contar las invocaciones que el
     * constructor hace al codificador
     *
     * @param policy política de los códigos
     * @param executor ejecutor de los envíos de correo
     * @return el servicio configurado
     */
    private PasswordServiceImpl serviceWith(RecoveryPolicy policy, Executor executor) {
        PasswordServiceImpl created = new PasswordServiceImpl(userRepository, codeRepository, passwordEncoder,
                emailService, new AuditRecorder(auditPublisher, Clock.fixed(NOW, ZoneOffset.UTC)),
                new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator()),
                Clock.fixed(NOW, ZoneOffset.UTC), policy, executor);
        clearInvocations(passwordEncoder);
        return created;
    }

    /**
     * Construye un usuario con la contraseña codificada
     *
     * @param id identificador del usuario
     * @param email correo del usuario
     * @param rawPassword contraseña en claro que se guarda codificada
     * @param state estado del usuario
     * @return el usuario construido
     */
    private UserEntity user(UUID id, String email, String rawPassword, UserState state) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setEmail(email);
        entity.setPassword(passwordEncoder.encode(rawPassword));
        entity.setState(state);
        entity.setRoles(EnumSet.of(UserRole.ESTUDIANTE));
        return entity;
    }

    /**
     * Construye un código de recuperación del usuario
     *
     * @param plainCode código en claro que se guarda codificado
     * @param expiresAt instante de expiración
     * @param attempts intentos fallidos acumulados
     * @param usedAt instante de uso, null si no se ha usado
     * @return el código construido
     */
    private PasswordResetCodeEntity storedCode(String plainCode, Instant expiresAt, int attempts, Instant usedAt) {
        PasswordResetCodeEntity code = new PasswordResetCodeEntity();
        code.setUserId(userId);
        code.setCodeHash(passwordEncoder.encode(plainCode));
        code.setAttempts(attempts);
        code.setCreatedAt(expiresAt.minusSeconds(POLICY.codeTtlMinutes() * 60L));
        code.setExpiresAt(expiresAt);
        code.setUsedAt(usedAt);
        return code;
    }

    /**
     * Deja un código vigente como el último sin usar del usuario
     *
     * @return el código guardado
     */
    private PasswordResetCodeEntity givenActiveCode() {
        PasswordResetCodeEntity code = storedCode(CODE, NOW.plusSeconds(600), 0, null);
        when(codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(code));
        return code;
    }

    /**
     * Construye una solicitud de código
     *
     * @param email correo de la solicitud
     * @return la solicitud construida
     */
    private ForgotPasswordRequest forgot(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    /**
     * Construye una petición de restablecimiento
     *
     * @param email correo de la petición
     * @param code código de la petición
     * @param password nueva contraseña
     * @param confirmation confirmación de la nueva contraseña
     * @return la petición construida
     */
    private ResetPasswordRequest reset(String email, String code, String password, String confirmation) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(email);
        request.setCode(code);
        request.setNewPassword(password);
        request.setNewPasswordConfirmation(confirmation);
        return request;
    }

    /**
     * Construye una petición de restablecimiento válida
     *
     * @return la petición construida
     */
    private ResetPasswordRequest validReset() {
        return reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD);
    }

    /**
     * Construye una petición de cambio de contraseña
     *
     * @param current contraseña actual
     * @param password nueva contraseña
     * @param confirmation confirmación de la nueva contraseña
     * @return la petición construida
     */
    private ChangePasswordRequest change(String current, String password, String confirmation) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(password);
        request.setNewPasswordConfirmation(confirmation);
        return request;
    }

    /**
     * Construye una petición de cambio de contraseña válida
     *
     * @return la petición construida
     */
    private ChangePasswordRequest validChange() {
        return change(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
    }

    /**
     * Construye la identidad del usuario que cambia su contraseña
     *
     * @return la identidad con el rol ESTUDIANTE
     */
    private RequesterIdentity requester() {
        return new RequesterIdentity(userId, EnumSet.of(UserRole.ESTUDIANTE));
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
     * Captura el código que el servicio entrega al servicio de correo
     *
     * @return el código enviado por correo
     */
    private String sentCode() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendRecoveryCode(eq(EMAIL), captor.capture(), eq(15));
        return captor.getValue();
    }

    /**
     * Captura el código de recuperación que el servicio guarda
     *
     * @return el código guardado
     */
    private PasswordResetCodeEntity savedCode() {
        ArgumentCaptor<PasswordResetCodeEntity> captor = ArgumentCaptor.forClass(PasswordResetCodeEntity.class);
        verify(codeRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    /**
     * Obtiene los errores por campo de una petición rechazada
     *
     * @param error excepción lanzada por el servicio
     * @param field nombre del campo en el contrato
     * @return el mensaje del campo, null si el campo no tiene error
     */
    private String messageOf(InvalidRequestException error, String field) {
        for (ErrorResponse.ErrorDetail detail : error.getErrors()) {
            if (detail.campo().equals(field)) {
                return detail.mensaje();
            }
        }
        return null;
    }

    /**
     * Una solicitud para una cuenta activa guarda solo el hash de un código de 6 dígitos con la vigencia
     * configurada y lo envía por correo
     */
    @Test
    void requestRecoveryCode_activeAccount_storesOnlyTheHashAndSendsTheCode() {
        service.requestRecoveryCode(forgot(EMAIL), CORR);

        String code = sentCode();
        PasswordResetCodeEntity saved = savedCode();
        assertTrue(code.matches("^[0-9]{6}$"));
        assertEquals(userId, saved.getUserId());
        assertNotEquals(code, saved.getCodeHash());
        assertTrue(passwordEncoder.matches(code, saved.getCodeHash()));
        assertEquals(0, saved.getAttempts());
        assertEquals(NOW, saved.getCreatedAt());
        assertEquals(NOW.plusSeconds(15 * 60), saved.getExpiresAt());
        assertNull(saved.getUsedAt());
    }

    /**
     * El código siempre tiene exactamente 6 dígitos, también cuando empieza por ceros
     */
    @RepeatedTest(25)
    void requestRecoveryCode_generatedCode_alwaysHasSixDigits() {
        service.requestRecoveryCode(forgot(EMAIL), CORR);

        assertTrue(sentCode().matches("^[0-9]{6}$"));
    }

    /**
     * Al generar un código nuevo se invalidan antes los anteriores, solo hay un código vigente (RN-MS01-21)
     */
    @Test
    void requestRecoveryCode_newCode_invalidatesThePreviousOnesBeforeSavingTheNewOne() {
        service.requestRecoveryCode(forgot(EMAIL), CORR);

        InOrder order = inOrder(codeRepository);
        order.verify(codeRepository).invalidateUnused(userId, NOW);
        order.verify(codeRepository).saveAndFlush(any(PasswordResetCodeEntity.class));
    }

    /**
     * El correo se busca normalizado: recortado y en minúsculas (RG-01)
     */
    @Test
    void requestRecoveryCode_mixedCaseEmail_isLookedUpNormalized() {
        service.requestRecoveryCode(forgot("  Maria.Perez@EscuelaIng.edu.co "), CORR);

        verify(userRepository).findByEmail(EMAIL);
        assertNotNull(sentCode());
    }

    /**
     * Un correo que no está registrado no guarda ni envía nada y la solicitud termina sin error (RN-MS01-20)
     */
    @Test
    void requestRecoveryCode_unknownEmail_storesAndSendsNothingWithoutFailing() {
        when(userRepository.findByEmail("nadie@escuelaing.edu.co")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestRecoveryCode(forgot("nadie@escuelaing.edu.co"), CORR));

        verifyNoInteractions(codeRepository, emailService);
    }

    /**
     * Una cuenta inactiva no recibe código y la solicitud termina sin error (RN-MS01-20)
     */
    @Test
    void requestRecoveryCode_inactiveAccount_storesAndSendsNothingWithoutFailing() {
        user.setState(UserState.INACTIVO);

        assertDoesNotThrow(() -> service.requestRecoveryCode(forgot(EMAIL), CORR));

        verifyNoInteractions(codeRepository, emailService);
    }

    /**
     * Si el último código se generó hace menos del tiempo de espera no se genera otro ni se envía otro correo
     */
    @Test
    void requestRecoveryCode_withinTheCooldown_doesNotIssueAnotherCode() {
        PasswordResetCodeEntity last = storedCode(CODE, NOW.plusSeconds(600), 0, null);
        last.setCreatedAt(NOW.minusSeconds(59));
        when(codeRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(last));

        assertDoesNotThrow(() -> service.requestRecoveryCode(forgot(EMAIL), CORR));

        verify(codeRepository, never()).invalidateUnused(any(UUID.class), any(Instant.class));
        verify(codeRepository, never()).saveAndFlush(any(PasswordResetCodeEntity.class));
        verifyNoInteractions(emailService);
    }

    /**
     * Cuando se cumple el tiempo de espera se genera un código nuevo
     */
    @Test
    void requestRecoveryCode_afterTheCooldown_issuesANewCode() {
        PasswordResetCodeEntity last = storedCode(CODE, NOW.plusSeconds(600), 0, null);
        last.setCreatedAt(NOW.minusSeconds(60));
        when(codeRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(last));

        service.requestRecoveryCode(forgot(EMAIL), CORR);

        assertNotNull(sentCode());
    }

    /**
     * Con la espera desactivada se genera un código nuevo en cada solicitud sin consultar el último
     */
    @Test
    void requestRecoveryCode_cooldownDisabled_issuesACodeEveryTime() {
        PasswordServiceImpl withoutCooldown = serviceWith(new RecoveryPolicy(15, 5, 0), Runnable::run);

        withoutCooldown.requestRecoveryCode(forgot(EMAIL), CORR);
        withoutCooldown.requestRecoveryCode(forgot(EMAIL), CORR);

        verify(emailService, times(2)).sendRecoveryCode(eq(EMAIL), anyString(), eq(15));
        verify(codeRepository, never()).findFirstByUserIdOrderByCreatedAtDesc(any(UUID.class));
    }

    /**
     * La vigencia del código sale de la política configurada
     */
    @Test
    void requestRecoveryCode_customTtl_isUsedForTheExpirationAndTheEmail() {
        PasswordServiceImpl custom = serviceWith(new RecoveryPolicy(30, 5, 60), Runnable::run);

        custom.requestRecoveryCode(forgot(EMAIL), CORR);

        assertEquals(NOW.plusSeconds(30 * 60), savedCode().getExpiresAt());
        verify(emailService).sendRecoveryCode(eq(EMAIL), anyString(), eq(30));
    }

    /**
     * Un correo con formato inválido se rechaza con el mensaje del formulario F-04 sin consultar nada
     */
    @Test
    void requestRecoveryCode_invalidEmail_isRejectedWithTheFormMessage() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.requestRecoveryCode(forgot("no-es-un-correo"), CORR));

        assertEquals("Ingrese un correo válido.", messageOf(error, "correo"));
        verifyNoInteractions(userRepository, codeRepository, emailService);
    }

    /**
     * Un campo que el contrato no define se rechaza (RG-04)
     */
    @Test
    void requestRecoveryCode_unknownField_isRejected() {
        ForgotPasswordRequest request = forgot(EMAIL);
        request.registerUnknownField("rol", "ADMIN");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.requestRecoveryCode(request, CORR));

        assertNotNull(messageOf(error, "rol"));
        verifyNoInteractions(userRepository, emailService);
    }

    /**
     * La solicitud de código no se audita: solo el restablecimiento y el cambio tienen evento
     */
    @Test
    void requestRecoveryCode_isNotAudited() {
        service.requestRecoveryCode(forgot(EMAIL), CORR);
        service.requestRecoveryCode(forgot("nadie@escuelaing.edu.co"), CORR);

        verifyNoInteractions(auditPublisher);
    }

    /**
     * La rama que genera un código calcula un hash del código y no verifica ninguno
     */
    @Test
    void requestRecoveryCode_issuingBranch_spendsOneHashOfWork() {
        service.requestRecoveryCode(forgot(EMAIL), CORR);

        verify(passwordEncoder, times(1)).encode(anyString());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    /**
     * Un correo no registrado gasta un hash de trabajo, igual que la rama que genera el código, así el tiempo de
     * respuesta no revela si el correo está registrado (RN-MS01-20)
     */
    @Test
    void requestRecoveryCode_unknownEmail_spendsOneHashOfWork() {
        service.requestRecoveryCode(forgot("nadie@escuelaing.edu.co"), CORR);

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    /**
     * Una cuenta inactiva gasta un hash de trabajo, igual que la rama que genera el código (RN-MS01-20)
     */
    @Test
    void requestRecoveryCode_inactiveAccount_spendsOneHashOfWork() {
        user.setState(UserState.INACTIVO);

        service.requestRecoveryCode(forgot(EMAIL), CORR);

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    /**
     * Una solicitud dentro de la espera mínima gasta un hash de trabajo, igual que la rama que genera el código
     * (RN-MS01-20)
     */
    @Test
    void requestRecoveryCode_withinTheCooldown_spendsOneHashOfWork() {
        PasswordResetCodeEntity last = storedCode(CODE, NOW.plusSeconds(600), 0, null);
        last.setCreatedAt(NOW.minusSeconds(5));
        when(codeRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(last));
        clearInvocations(passwordEncoder);

        service.requestRecoveryCode(forgot(EMAIL), CORR);

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    /**
     * El correo no se envía en el hilo de la petición: se entrega al ejecutor y la solicitud termina antes
     */
    @Test
    void requestRecoveryCode_activeAccount_handsTheEmailToTheExecutor() {
        AtomicReference<Runnable> pending = new AtomicReference<>();
        PasswordServiceImpl asynchronous = serviceWith(POLICY, pending::set);

        asynchronous.requestRecoveryCode(forgot(EMAIL), CORR);

        verifyNoInteractions(emailService);
        assertNotNull(pending.get());

        pending.get().run();

        assertNotNull(sentCode());
    }

    /**
     * El correo solo se entrega al ejecutor después de confirmar la transacción, nunca se envía un código que no
     * quedó guardado
     */
    @Test
    void requestRecoveryCode_insideATransaction_sendsTheEmailOnlyAfterTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.requestRecoveryCode(forgot(EMAIL), CORR);

        verifyNoInteractions(emailService);

        TransactionSynchronizationUtils.triggerAfterCommit();

        assertNotNull(sentCode());
    }

    /**
     * Si la transacción se revierte el correo nunca se envía
     */
    @Test
    void requestRecoveryCode_transactionRolledBack_neverSendsTheEmail() {
        TransactionSynchronizationManager.initSynchronization();

        service.requestRecoveryCode(forgot(EMAIL), CORR);
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(emailService);
    }

    /**
     * Si el servidor de correo falla la solicitud termina igual y la respuesta no cambia
     */
    @Test
    void requestRecoveryCode_whenTheMailServerFails_stillCompletes() {
        doThrow(new MailSendException("SMTP caído")).when(emailService)
                .sendRecoveryCode(anyString(), anyString(), anyInt());

        assertDoesNotThrow(() -> service.requestRecoveryCode(forgot(EMAIL), CORR));
    }

    /**
     * Si la cola de envíos está llena la solicitud termina igual y la respuesta no cambia
     */
    @Test
    void requestRecoveryCode_whenTheMailQueueIsFull_stillCompletes() {
        Executor full = task -> {
            throw new RejectedExecutionException("cola llena");
        };
        PasswordServiceImpl saturated = serviceWith(POLICY, full);

        assertDoesNotThrow(() -> saturated.requestRecoveryCode(forgot(EMAIL), CORR));

        verifyNoInteractions(emailService);
    }

    /**
     * El envío se hace con el correlation id de la petición en el contexto de logs del hilo de envío, y el
     * contexto se limpia al terminar
     */
    @Test
    void requestRecoveryCode_delivery_runsWithTheCorrelationIdInTheLogContext() {
        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(call -> {
            seen.set(MDC.get(CorrelationIds.MDC_KEY));
            return null;
        }).when(emailService).sendRecoveryCode(anyString(), anyString(), anyInt());

        service.requestRecoveryCode(forgot(EMAIL), CORR);

        assertEquals(CORR, seen.get());
        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }

    /**
     * El código y el correo nunca se escriben en los logs, ni cuando el envío falla ni cuando se rechaza un
     * restablecimiento
     */
    @Test
    void recoveryFlow_neverWritesTheCodeThePasswordsOrTheEmailToTheLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(PasswordServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            doThrow(new MailSendException("550 5.1.1 <" + EMAIL + "> rechazado")).when(emailService)
                    .sendRecoveryCode(anyString(), anyString(), anyInt());
            service.requestRecoveryCode(forgot(EMAIL), CORR);
            String issued = savedCode().getCodeHash();

            givenActiveCode();
            assertThrows(InvalidResetCodeException.class,
                    () -> service.resetPassword(reset(EMAIL, "000000", NEW_PASSWORD, NEW_PASSWORD), CORR));
            service.resetPassword(validReset(), CORR);
            assertThrows(InvalidRequestException.class,
                    () -> service.changePassword(requester(), change("Incorrecta#1", NEW_PASSWORD, NEW_PASSWORD),
                            CORR));

            List<String> messages = new ArrayList<>();
            for (ILoggingEvent event : appender.list) {
                messages.add(event.getFormattedMessage());
            }
            assertFalse(messages.isEmpty());
            for (String message : messages) {
                assertFalse(message.contains(EMAIL));
                assertFalse(message.contains(CODE));
                assertFalse(message.contains("000000"));
                assertFalse(message.contains(issued));
                assertFalse(message.contains(OLD_PASSWORD));
                assertFalse(message.contains(NEW_PASSWORD));
                assertFalse(message.contains("Incorrecta#1"));
            }
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * Un código correcto actualiza la contraseña con su hash, consume el código y audita RECUPERACION exitosa
     * sin datos sensibles (flujo base A)
     */
    @Test
    void resetPassword_validCode_updatesThePasswordConsumesTheCodeAndAuditsTheSuccess() {
        PasswordResetCodeEntity code = givenActiveCode();

        service.resetPassword(validReset(), CORR);

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
        assertNotEquals(NEW_PASSWORD, user.getPassword());
        assertEquals(NOW, code.getUsedAt());
        verify(userRepository).saveAndFlush(user);
        verify(codeRepository).saveAndFlush(code);
        verify(codeRepository).invalidateUnused(userId, NOW);

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.RECUPERACION, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(userId, event.usuarioId());
        assertEquals("usuario:" + userId, event.recursoAfectado());
        assertEquals(CORR, event.correlationId());
        assertEquals(NOW, event.fecha());
        assertNull(event.detalle());
        assertNull(event.usuarioRoles());
    }

    /**
     * Después de restablecer la contraseña anterior deja de funcionar y la nueva funciona
     */
    @Test
    void resetPassword_validCode_theOldPasswordStopsWorkingAndTheNewOneWorks() {
        givenActiveCode();

        service.resetPassword(validReset(), CORR);

        assertFalse(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
    }

    /**
     * Un código incorrecto responde con el error genérico, cuenta un intento, no cambia la contraseña y audita el
     * fallo (flujo alterno A1)
     */
    @Test
    void resetPassword_wrongCode_rejectsCountsTheAttemptAndAuditsTheFailure() {
        PasswordResetCodeEntity code = givenActiveCode();
        String previousHash = user.getPassword();

        InvalidResetCodeException error = assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, "000000", NEW_PASSWORD, NEW_PASSWORD), CORR));

        assertEquals("Código incorrecto o vencido.", error.getMessage());
        assertEquals(1, code.getAttempts());
        assertNull(code.getUsedAt());
        assertEquals(previousHash, user.getPassword());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.RECUPERACION, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(userId, event.usuarioId());
        assertEquals("CODIGO_INVALIDO", event.detalle());
    }

    /**
     * Al llegar al máximo de intentos fallidos el código se invalida y ya no sirve ni el correcto
     */
    @Test
    void resetPassword_lastAllowedWrongAttempt_invalidatesTheCode() {
        PasswordResetCodeEntity code = storedCode(CODE, NOW.plusSeconds(600), 4, null);
        when(codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(code));

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, "000000", NEW_PASSWORD, NEW_PASSWORD), CORR));

        assertEquals(5, code.getAttempts());
        assertEquals(NOW, code.getUsedAt());
        assertFalse(code.isActiveAt(NOW));
    }

    /**
     * Antes del último intento el código sigue vigente
     */
    @Test
    void resetPassword_wrongAttemptBeforeTheLimit_keepsTheCodeActive() {
        PasswordResetCodeEntity code = storedCode(CODE, NOW.plusSeconds(600), 3, null);
        when(codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(code));

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, "000000", NEW_PASSWORD, NEW_PASSWORD), CORR));

        assertEquals(4, code.getAttempts());
        assertTrue(code.isActiveAt(NOW));
    }

    /**
     * Un código vencido se rechaza con el error genérico aunque sea el correcto, justo en el instante de
     * expiración ya no es válido
     */
    @Test
    void resetPassword_expiredCode_isRejectedEvenIfItIsTheRightOne() {
        PasswordResetCodeEntity code = storedCode(CODE, NOW, 0, null);
        when(codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(code));

        assertThrows(InvalidResetCodeException.class, () -> service.resetPassword(validReset(), CORR));

        assertNull(code.getUsedAt());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        assertEquals(AuditResult.FALLO, singleAuditEvent().resultado());
    }

    /**
     * Un código ya usado se rechaza con el error genérico
     */
    @Test
    void resetPassword_alreadyUsedCode_isRejected() {
        PasswordResetCodeEntity code = storedCode(CODE, NOW.plusSeconds(600), 0, NOW.minusSeconds(30));
        when(codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(code));

        assertThrows(InvalidResetCodeException.class, () -> service.resetPassword(validReset(), CORR));

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    /**
     * Un código no se puede usar dos veces: tras un restablecimiento exitoso queda consumido
     */
    @Test
    void resetPassword_sameCodeTwice_theSecondUseIsRejected() {
        PasswordResetCodeEntity code = givenActiveCode();
        service.resetPassword(validReset(), CORR);
        when(codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(code));

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, "Otra#2026xyz", "Otra#2026xyz"), CORR));
    }

    /**
     * Si el usuario no tiene ningún código sin usar se rechaza con el error genérico
     */
    @Test
    void resetPassword_userWithoutAnyCode_isRejected() {
        when(codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.empty());

        assertThrows(InvalidResetCodeException.class, () -> service.resetPassword(validReset(), CORR));

        AuditEvent event = singleAuditEvent();
        assertEquals("CODIGO_INVALIDO", event.detalle());
        assertEquals(userId, event.usuarioId());
    }

    /**
     * Un correo que no está registrado recibe exactamente el mismo error que un código incorrecto y el evento
     * de auditoría no lleva usuario
     */
    @Test
    void resetPassword_unknownEmail_isRejectedWithTheSameErrorAndAuditsWithoutUser() {
        when(userRepository.findByEmail("nadie@escuelaing.edu.co")).thenReturn(Optional.empty());

        InvalidResetCodeException error = assertThrows(InvalidResetCodeException.class, () -> service
                .resetPassword(reset("nadie@escuelaing.edu.co", CODE, NEW_PASSWORD, NEW_PASSWORD), CORR));

        assertEquals("Código incorrecto o vencido.", error.getMessage());
        verifyNoInteractions(codeRepository);
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertNull(event.usuarioId());
        assertNull(event.recursoAfectado());
        assertEquals("CODIGO_INVALIDO", event.detalle());
    }

    /**
     * Una cuenta inactiva recibe el mismo error genérico aunque tenga un código vigente
     */
    @Test
    void resetPassword_inactiveAccount_isRejectedWithTheSameError() {
        givenActiveCode();
        user.setState(UserState.INACTIVO);

        InvalidResetCodeException error = assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(validReset(), CORR));

        assertEquals("Código incorrecto o vencido.", error.getMessage());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    /**
     * Un correo no registrado gasta un solo hash de trabajo, igual que un código incorrecto, así el tiempo no
     * distingue las causas del rechazo
     */
    @Test
    void resetPassword_unknownEmail_spendsOneHashOfWork() {
        when(userRepository.findByEmail("nadie@escuelaing.edu.co")).thenReturn(Optional.empty());

        assertThrows(InvalidResetCodeException.class, () -> service
                .resetPassword(reset("nadie@escuelaing.edu.co", CODE, NEW_PASSWORD, NEW_PASSWORD), CORR));

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    /**
     * Una cuenta inactiva gasta un solo hash de trabajo, igual que un código incorrecto
     */
    @Test
    void resetPassword_inactiveAccount_spendsOneHashOfWork() {
        user.setState(UserState.INACTIVO);

        assertThrows(InvalidResetCodeException.class, () -> service.resetPassword(validReset(), CORR));

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    /**
     * Un usuario sin código gasta un solo hash de trabajo, igual que un código incorrecto
     */
    @Test
    void resetPassword_userWithoutCode_spendsOneHashOfWork() {
        assertThrows(InvalidResetCodeException.class, () -> service.resetPassword(validReset(), CORR));

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    /**
     * Un código vencido gasta un solo hash de trabajo, igual que un código incorrecto
     */
    @Test
    void resetPassword_expiredCode_spendsOneHashOfWork() {
        PasswordResetCodeEntity expired = storedCode(CODE, NOW.minusSeconds(1), 0, null);
        when(codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(expired));
        clearInvocations(passwordEncoder);

        assertThrows(InvalidResetCodeException.class, () -> service.resetPassword(validReset(), CORR));

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    /**
     * Un código incorrecto gasta un solo hash de trabajo, el de la verificación
     */
    @Test
    void resetPassword_wrongCode_spendsOneHashOfWork() {
        givenActiveCode();
        clearInvocations(passwordEncoder);

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, "000000", NEW_PASSWORD, NEW_PASSWORD), CORR));

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    /**
     * Una nueva contraseña que incumple la política se rechaza con el mensaje del formulario F-05, sin buscar el
     * código, y se audita (flujo alterno A2)
     */
    @Test
    void resetPassword_weakNewPassword_isRejectedWithThePolicyMessageAndAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, "debil", "debil"), CORR));

        assertEquals("La contraseña debe tener 8 a 64 caracteres, con mayúscula, minúscula, número y carácter "
                + "especial.", messageOf(error, "nueva_contrasena"));
        verifyNoInteractions(userRepository, codeRepository);
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.RECUPERACION, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertNull(event.usuarioId());
        assertEquals("VALIDACION", event.detalle());
    }

    /**
     * Una confirmación distinta se rechaza con el mensaje del formulario F-05 y no consume ni cuenta el código
     * (flujo alterno A2)
     */
    @Test
    void resetPassword_confirmationDoesNotMatch_isRejectedWithoutTouchingTheCode() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, NEW_PASSWORD, "Otra#2026xyz"), CORR));

        assertEquals("Las contraseñas no coinciden.", messageOf(error, "confirmar_nueva_contrasena"));
        verifyNoInteractions(codeRepository);
        assertEquals("VALIDACION", singleAuditEvent().detalle());
    }

    /**
     * Un código con formato inválido se rechaza como dato inválido con el mensaje del formulario F-05
     */
    @Test
    void resetPassword_malformedCode_isRejectedAsInvalidData() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.resetPassword(reset(EMAIL, "12ab", NEW_PASSWORD, NEW_PASSWORD), CORR));

        assertEquals("Código incorrecto o vencido.", messageOf(error, "codigo"));
        verifyNoInteractions(userRepository, codeRepository);
    }

    /**
     * Los campos faltantes se informan todos a la vez
     */
    @Test
    void resetPassword_emptyBody_reportsEveryRequiredField() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.resetPassword(new ResetPasswordRequest(), CORR));

        assertNotNull(messageOf(error, "correo"));
        assertNotNull(messageOf(error, "codigo"));
        assertNotNull(messageOf(error, "nueva_contrasena"));
        assertNotNull(messageOf(error, "confirmar_nueva_contrasena"));
    }

    /**
     * Un campo que el contrato no define se rechaza (RG-04)
     */
    @Test
    void resetPassword_unknownField_isRejected() {
        ResetPasswordRequest request = validReset();
        request.registerUnknownField("confirmar_contrasena", "x");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.resetPassword(request, CORR));

        assertNotNull(messageOf(error, "confirmar_contrasena"));
        verifyNoInteractions(userRepository, codeRepository);
    }

    /**
     * Si la nueva contraseña es igual a la actual y el código es correcto se rechaza como dato inválido sin
     * consumir el código, y se audita (flujo alterno A2)
     */
    @Test
    void resetPassword_newPasswordEqualToTheCurrent_isRejectedWithoutConsumingTheCode() {
        PasswordResetCodeEntity code = givenActiveCode();

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.resetPassword(reset(EMAIL, CODE, OLD_PASSWORD, OLD_PASSWORD), CORR));

        assertEquals("La nueva contraseña no cumple la política o es igual a la actual.",
                messageOf(error, "nueva_contrasena"));
        assertNull(code.getUsedAt());
        assertEquals(0, code.getAttempts());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("NUEVA_IGUAL_A_ACTUAL", event.detalle());
        assertEquals(userId, event.usuarioId());
    }

    /**
     * La igualdad con la contraseña actual solo se revisa con un código correcto: con uno incorrecto el error
     * es el genérico y no revela nada de la contraseña
     */
    @Test
    void resetPassword_equalToTheCurrentWithAWrongCode_doesNotRevealAnythingAboutThePassword() {
        givenActiveCode();

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, "000000", OLD_PASSWORD, OLD_PASSWORD), CORR));
    }

    /**
     * Si la auditoría falla el restablecimiento se completa igual, la respuesta no cambia
     */
    @Test
    void resetPassword_whenTheAuditFails_stillCompletes() {
        givenActiveCode();
        doThrow(new IllegalStateException("MS08 caído")).when(auditPublisher).publish(any(AuditEvent.class));

        assertDoesNotThrow(() -> service.resetPassword(validReset(), CORR));

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
    }

    /**
     * El evento de auditoría no contiene la contraseña, el código ni el hash
     */
    @Test
    void resetPassword_auditEvent_neverContainsPasswordsCodesOrHashes() {
        givenActiveCode();

        service.resetPassword(validReset(), CORR);

        String event = singleAuditEvent().toString();
        assertFalse(event.contains(NEW_PASSWORD));
        assertFalse(event.contains(OLD_PASSWORD));
        assertFalse(event.contains(CODE));
        assertFalse(event.contains(user.getPassword()));
        assertFalse(event.contains(EMAIL));
    }

    /**
     * Sin X-Correlation-ID el servicio genera uno válido para el evento
     */
    @Test
    void resetPassword_withoutCorrelationId_generatesOneForTheEvent() {
        givenActiveCode();

        service.resetPassword(validReset(), null);

        String generated = singleAuditEvent().correlationId();
        assertEquals(generated, UUID.fromString(generated).toString());
        assertEquals('4', generated.charAt(14));
    }

    /**
     * El conteo de intentos fallidos debe sobrevivir al rechazo: la transacción no se revierte con el error del
     * código inválido, de lo contrario el límite de intentos no serviría
     *
     * @throws NoSuchMethodException si el método del servicio no existe
     */
    @Test
    void resetPassword_isTransactionalAndKeepsTheFailedAttemptsWhenTheCodeIsRejected()
            throws NoSuchMethodException {
        Method method = PasswordServiceImpl.class.getMethod("resetPassword", ResetPasswordRequest.class,
                String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(List.of(InvalidResetCodeException.class), List.of(transactional.noRollbackFor()));
    }

    /**
     * Una contraseña correcta actual y una nueva válida actualizan la contraseña con su hash y auditan
     * CAMBIO_CONTRASENA exitoso con la identidad del solicitante (flujo base B)
     */
    @Test
    void changePassword_validRequest_updatesThePasswordAndAuditsTheSuccess() {
        service.changePassword(requester(), validChange(), CORR);

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
        verify(userRepository).saveAndFlush(user);

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.CAMBIO_CONTRASENA, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(userId, event.usuarioId());
        assertEquals("ESTUDIANTE", event.usuarioRoles());
        assertEquals("usuario:" + userId, event.recursoAfectado());
        assertEquals(CORR, event.correlationId());
        assertEquals(NOW, event.fecha());
        assertNull(event.detalle());
    }

    /**
     * Cualquier rol del catálogo puede cambiar su propia contraseña
     *
     * @param role rol del solicitante
     */
    @ParameterizedTest
    @EnumSource(UserRole.class)
    void changePassword_anyRole_isAccepted(UserRole role) {
        RequesterIdentity identity = new RequesterIdentity(userId, EnumSet.of(role));

        assertDoesNotThrow(() -> service.changePassword(identity, validChange(), CORR));

        assertEquals(role.name(), singleAuditEvent().usuarioRoles());
    }

    /**
     * Una contraseña actual incorrecta se rechaza con el mensaje del formulario F-06 en su campo, no cambia la
     * contraseña y se audita (flujo alterno A3)
     */
    @Test
    void changePassword_wrongCurrentPassword_isRejectedAndAudited() {
        String previousHash = user.getPassword();

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.changePassword(requester(), change("Incorrecta#1", NEW_PASSWORD, NEW_PASSWORD),
                        CORR));

        assertEquals("La contraseña actual es incorrecta.", messageOf(error, "contrasena_actual"));
        assertEquals(1, error.getErrors().size());
        assertEquals(previousHash, user.getPassword());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CONTRASENA_ACTUAL_INCORRECTA", event.detalle());
        assertEquals(userId, event.usuarioId());
    }

    /**
     * Una nueva contraseña igual a la actual se rechaza con el mensaje del formulario F-06 y se audita (flujo
     * alterno A2)
     */
    @Test
    void changePassword_newPasswordEqualToTheCurrent_isRejectedAndAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.changePassword(requester(), change(OLD_PASSWORD, OLD_PASSWORD, OLD_PASSWORD), CORR));

        assertEquals("La nueva contraseña no cumple la política o es igual a la actual.",
                messageOf(error, "nueva_contrasena"));
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        assertEquals("NUEVA_IGUAL_A_ACTUAL", singleAuditEvent().detalle());
    }

    /**
     * Una nueva contraseña que incumple la política se rechaza con el mensaje del formulario F-06, sin consultar
     * al usuario, y se audita (flujo alterno A2)
     */
    @Test
    void changePassword_weakNewPassword_isRejectedWithTheFormMessageAndAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.changePassword(requester(), change(OLD_PASSWORD, "debil", "debil"), CORR));

        assertEquals("La nueva contraseña no cumple la política o es igual a la actual.",
                messageOf(error, "nueva_contrasena"));
        verifyNoInteractions(userRepository);
        assertEquals("VALIDACION", singleAuditEvent().detalle());
    }

    /**
     * Una confirmación distinta se rechaza con el mensaje del formulario F-06 y se audita (flujo alterno A2)
     */
    @Test
    void changePassword_confirmationDoesNotMatch_isRejectedAndAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.changePassword(requester(), change(OLD_PASSWORD, NEW_PASSWORD, "Otra#2026xyz"),
                        CORR));

        assertEquals("Las contraseñas no coinciden.", messageOf(error, "confirmar_nueva_contrasena"));
        verifyNoInteractions(userRepository);
        assertEquals("VALIDACION", singleAuditEvent().detalle());
    }

    /**
     * Los campos faltantes se informan todos a la vez
     */
    @Test
    void changePassword_emptyBody_reportsEveryRequiredField() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.changePassword(requester(), new ChangePasswordRequest(), CORR));

        assertNotNull(messageOf(error, "contrasena_actual"));
        assertNotNull(messageOf(error, "nueva_contrasena"));
        assertNotNull(messageOf(error, "confirmar_nueva_contrasena"));
    }

    /**
     * Un campo que el contrato no define, como un id de usuario, se rechaza: la contraseña que se cambia es
     * siempre la del usuario identificado por las cabeceras (RG-04)
     */
    @Test
    void changePassword_unknownField_isRejected() {
        ChangePasswordRequest request = validChange();
        request.registerUnknownField("id", UUID.randomUUID().toString());

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.changePassword(requester(), request, CORR));

        assertNotNull(messageOf(error, "id"));
        verifyNoInteractions(userRepository);
    }

    /**
     * Sin el permiso requerido se responde 403 sin validar ni consultar nada, y se audita el rechazo
     */
    @Test
    void changePassword_withoutThePermission_isForbiddenAndAudited() {
        RequesterIdentity withoutRoles = new RequesterIdentity(userId, EnumSet.noneOf(UserRole.class));

        assertThrows(ForbiddenOperationException.class,
                () -> service.changePassword(withoutRoles, validChange(), CORR));

        verifyNoInteractions(userRepository);
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.CAMBIO_CONTRASENA, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("SIN_PERMISO", event.detalle());
    }

    /**
     * Si el usuario del token ya no existe se responde 404
     */
    @Test
    void changePassword_userNoLongerExists_isNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.changePassword(requester(), validChange(), CORR));

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    /**
     * Si la auditoría falla el cambio se completa igual, la respuesta no cambia
     */
    @Test
    void changePassword_whenTheAuditFails_stillCompletes() {
        doThrow(new IllegalStateException("MS08 caído")).when(auditPublisher).publish(any(AuditEvent.class));

        assertDoesNotThrow(() -> service.changePassword(requester(), validChange(), CORR));

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
    }

    /**
     * Ningún evento de auditoría del cambio contiene contraseñas ni hashes
     */
    @Test
    void changePassword_auditEvents_neverContainPasswordsOrHashes() {
        service.changePassword(requester(), validChange(), CORR);

        String event = singleAuditEvent().toString();
        assertFalse(event.contains(NEW_PASSWORD));
        assertFalse(event.contains(OLD_PASSWORD));
        assertFalse(event.contains(user.getPassword()));
    }

    /**
     * El cambio de contraseña no envía correos ni toca los códigos de recuperación
     */
    @Test
    void changePassword_doesNotTouchTheRecoveryCodesNorSendEmails() {
        service.changePassword(requester(), validChange(), CORR);

        verifyNoInteractions(codeRepository, emailService);
    }
}
