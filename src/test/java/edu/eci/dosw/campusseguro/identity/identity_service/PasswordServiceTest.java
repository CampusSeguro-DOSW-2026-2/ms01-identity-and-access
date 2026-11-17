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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
 * RF-MS01-08: recovery of the forgotten password (base flow A) and authenticated password change (base flow B),
 * alternate flows A1, A2 and A3, RN-MS01-20, RN-MS01-21, RN-MS01-22 and audit of each attempt
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
     * Prepares the service with simulated repositories, a real low-cost BCrypt encoder, a fixed clock and an executor
     * that sends the emails in the same thread
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
     * Leaves clean the transaction synchronization and the log context that some tests activate
     */
    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        MDC.clear();
    }

        /**
     * Builds the service with the indicated policy and executor, and leaves uncounted the invocations that the
     * constructor makes to the encoder
     *
     * @param policy policy of the codes
     * @param executor executor of the email sending
     * @return the configured service
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
     * Builds a user with the encoded password
     *
     * @param id identifier of the user
     * @param email email of the user
     * @param rawPassword password in clear text that is stored encoded
     * @param state status of the user
     * @return the built user
     */
    private UserEntity user(UUID id, String email, String rawPassword, UserState state) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setEmail(email);
        entity.setPassword(passwordEncoder.encode(rawPassword));
        entity.setState(state);
        entity.setRoles(TestEntities.roles("ESTUDIANTE"));
        return entity;
    }

        /**
     * Builds a recovery code of the user
     *
     * @param plainCode code in clear text that is stored encoded
     * @param expiresAt expiration instant
     * @param attempts accumulated failed attempts
     * @param usedAt instant of use, null if it has not been used
     * @return the built code
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
     * Leaves a valid code as the last unused one of the user
     *
     * @return the stored code
     */
    private PasswordResetCodeEntity givenActiveCode() {
        PasswordResetCodeEntity code = storedCode(CODE, NOW.plusSeconds(600), 0, null);
        when(codeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(code));
        return code;
    }

        /**
     * Builds a code request
     *
     * @param email email of the request
     * @return the built request
     */
    private ForgotPasswordRequest forgot(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

        /**
     * Builds a reset request
     *
     * @param email email of the request
     * @param code code of the request
     * @param password new password
     * @param confirmation confirmation of the new password
     * @return the built request
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
     * Builds a valid reset request
     *
     * @return the built request
     */
    private ResetPasswordRequest validReset() {
        return reset(EMAIL, CODE, NEW_PASSWORD, NEW_PASSWORD);
    }

        /**
     * Builds a password change request
     *
     * @param current current password
     * @param password new password
     * @param confirmation confirmation of the new password
     * @return the built request
     */
    private ChangePasswordRequest change(String current, String password, String confirmation) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(password);
        request.setNewPasswordConfirmation(confirmation);
        return request;
    }

        /**
     * Builds a valid password change request
     *
     * @return the built request
     */
    private ChangePasswordRequest validChange() {
        return change(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
    }

        /**
     * Builds the identity of the user who changes their password
     *
     * @return the identity with the role ESTUDIANTE
     */
    private RequesterIdentity requester() {
        return TestIdentities.of(userId, "ESTUDIANTE");
    }

        /**
     * Captures the only audit event emitted
     *
     * @return the emitted event
     */
    private AuditEvent singleAuditEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(captor.capture());
        return captor.getValue();
    }

        /**
     * Captures the code that the service delivers to the mail service
     *
     * @return the code sent by email
     */
    private String sentCode() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendRecoveryCode(eq(EMAIL), captor.capture(), eq(15));
        return captor.getValue();
    }

        /**
     * Captures the recovery code that the service stores
     *
     * @return the stored code
     */
    private PasswordResetCodeEntity savedCode() {
        ArgumentCaptor<PasswordResetCodeEntity> captor = ArgumentCaptor.forClass(PasswordResetCodeEntity.class);
        verify(codeRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

        /**
     * Gets the errors by field of a rejected request
     *
     * @param error exception thrown by the service
     * @param field name of the field in the contract
     * @return the message of the field, null if the field has no error
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
     * A request for an active account stores only the hash of a 6-digit code with the configured validity and sends it
     * by email
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
     * The code always has exactly 6 digits, also when it starts with zeros
     */
    @RepeatedTest(25)
    void requestRecoveryCode_generatedCode_alwaysHasSixDigits() {
        service.requestRecoveryCode(forgot(EMAIL), CORR);

        assertTrue(sentCode().matches("^[0-9]{6}$"));
    }

        /**
     * When a new code is generated the previous ones are invalidated first, there is only one valid code (RN-MS01-21)
     */
    @Test
    void requestRecoveryCode_newCode_invalidatesThePreviousOnesBeforeSavingTheNewOne() {
        service.requestRecoveryCode(forgot(EMAIL), CORR);

        InOrder order = inOrder(codeRepository);
        order.verify(codeRepository).invalidateUnused(userId, NOW);
        order.verify(codeRepository).saveAndFlush(any(PasswordResetCodeEntity.class));
    }

        /**
     * The email is looked up normalized: trimmed and in lowercase (RG-01)
     */
    @Test
    void requestRecoveryCode_mixedCaseEmail_isLookedUpNormalized() {
        service.requestRecoveryCode(forgot("  Maria.Perez@EscuelaIng.edu.co "), CORR);

        verify(userRepository).findByEmail(EMAIL);
        assertNotNull(sentCode());
    }

        /**
     * An email that is not registered stores and sends nothing and the request ends without error (RN-MS01-20)
     */
    @Test
    void requestRecoveryCode_unknownEmail_storesAndSendsNothingWithoutFailing() {
        when(userRepository.findByEmail("nadie@escuelaing.edu.co")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestRecoveryCode(forgot("nadie@escuelaing.edu.co"), CORR));

        verifyNoInteractions(codeRepository, emailService);
    }

        /**
     * An inactive account does not receive a code and the request ends without error (RN-MS01-20)
     */
    @Test
    void requestRecoveryCode_inactiveAccount_storesAndSendsNothingWithoutFailing() {
        user.setState(UserState.INACTIVO);

        assertDoesNotThrow(() -> service.requestRecoveryCode(forgot(EMAIL), CORR));

        verifyNoInteractions(codeRepository, emailService);
    }

        /**
     * If the last code was generated less than the wait time ago no other is generated and no other email is sent
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
     * When the wait time has passed a new code is generated
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
     * With the wait deactivated a new code is generated on each request without querying the last one
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
     * The validity of the code comes from the configured policy
     */
    @Test
    void requestRecoveryCode_customTtl_isUsedForTheExpirationAndTheEmail() {
        PasswordServiceImpl custom = serviceWith(new RecoveryPolicy(30, 5, 60), Runnable::run);

        custom.requestRecoveryCode(forgot(EMAIL), CORR);

        assertEquals(NOW.plusSeconds(30 * 60), savedCode().getExpiresAt());
        verify(emailService).sendRecoveryCode(eq(EMAIL), anyString(), eq(30));
    }

        /**
     * An email with an invalid format is rejected with the message of form F-04 without querying anything
     */
    @Test
    void requestRecoveryCode_invalidEmail_isRejectedWithTheFormMessage() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.requestRecoveryCode(forgot("no-es-un-correo"), CORR));

        assertEquals("Ingrese un correo válido.", messageOf(error, "correo"));
        verifyNoInteractions(userRepository, codeRepository, emailService);
    }

        /**
     * A field that the contract does not define is rejected (RG-04)
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
     * The code request is not audited: only the reset and the change have an event
     */
    @Test
    void requestRecoveryCode_isNotAudited() {
        service.requestRecoveryCode(forgot(EMAIL), CORR);
        service.requestRecoveryCode(forgot("nadie@escuelaing.edu.co"), CORR);

        verifyNoInteractions(auditPublisher);
    }

        /**
     * The branch that generates a code computes a hash of the code and does not verify any
     */
    @Test
    void requestRecoveryCode_issuingBranch_spendsOneHashOfWork() {
        service.requestRecoveryCode(forgot(EMAIL), CORR);

        verify(passwordEncoder, times(1)).encode(anyString());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

        /**
     * An unregistered email spends a work hash, like the branch that generates the code, so the response time does not
     * reveal whether the email is registered (RN-MS01-20)
     */
    @Test
    void requestRecoveryCode_unknownEmail_spendsOneHashOfWork() {
        service.requestRecoveryCode(forgot("nadie@escuelaing.edu.co"), CORR);

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

        /**
     * An inactive account spends a work hash, like the branch that generates the code (RN-MS01-20)
     */
    @Test
    void requestRecoveryCode_inactiveAccount_spendsOneHashOfWork() {
        user.setState(UserState.INACTIVO);

        service.requestRecoveryCode(forgot(EMAIL), CORR);

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

        /**
     * A request within the minimum wait spends a work hash, like the branch that generates the code (RN-MS01-20)
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
     * The email is not sent in the request thread: it is handed to the executor and the request ends earlier
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
     * The email is only handed to the executor after the transaction is committed, a code that was not stored is never
     * sent
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
     * If the transaction is rolled back the email is never sent
     */
    @Test
    void requestRecoveryCode_transactionRolledBack_neverSendsTheEmail() {
        TransactionSynchronizationManager.initSynchronization();

        service.requestRecoveryCode(forgot(EMAIL), CORR);
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(emailService);
    }

        /**
     * If the mail server fails the request ends the same and the response does not change
     */
    @Test
    void requestRecoveryCode_whenTheMailServerFails_stillCompletes() {
        doThrow(new MailSendException("SMTP down")).when(emailService)
                .sendRecoveryCode(anyString(), anyString(), anyInt());

        assertDoesNotThrow(() -> service.requestRecoveryCode(forgot(EMAIL), CORR));
    }

        /**
     * If the sending queue is full the request ends the same and the response does not change
     */
    @Test
    void requestRecoveryCode_whenTheMailQueueIsFull_stillCompletes() {
        Executor full = task -> {
            throw new RejectedExecutionException("queue full");
        };
        PasswordServiceImpl saturated = serviceWith(POLICY, full);

        assertDoesNotThrow(() -> saturated.requestRecoveryCode(forgot(EMAIL), CORR));

        verifyNoInteractions(emailService);
    }

        /**
     * The sending is done with the correlation id of the request in the log context of the sending thread, and the
     * context is cleared at the end
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
     * The code and the email are never written to the logs, neither when the sending fails nor when a reset is rejected
     */
    @Test
    void recoveryFlow_neverWritesTheCodeThePasswordsOrTheEmailToTheLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(PasswordServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            doThrow(new MailSendException("550 5.1.1 <" + EMAIL + "> rejected")).when(emailService)
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
     * A correct code updates the password with its hash, consumes the code and audits a successful RECUPERACION without
     * sensitive data (base flow A)
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
     * After the reset the previous password stops working and the new one works
     */
    @Test
    void resetPassword_validCode_theOldPasswordStopsWorkingAndTheNewOneWorks() {
        givenActiveCode();

        service.resetPassword(validReset(), CORR);

        assertFalse(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
    }

        /**
     * An incorrect code answers with the generic error, counts an attempt, does not change the password and audits the
     * failure (alternate flow A1)
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
     * When the maximum of failed attempts is reached the code is invalidated and not even the correct one works anymore
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
     * Before the last attempt the code is still valid
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
     * An expired code is rejected with the generic error even if it is the correct one, right at the expiration instant
     * it is no longer valid
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
     * An already used code is rejected with the generic error
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
     * A code cannot be used twice: after a successful reset it is consumed
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
     * If the user has no unused code it is rejected with the generic error
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
     * An email that is not registered receives exactly the same error as an incorrect code and the audit event carries
     * no user
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
     * An inactive account receives the same generic error even if it has a valid code
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
     * An unregistered email spends a single work hash, like an incorrect code, so the time does not distinguish the
     * causes of the rejection
     */
    @Test
    void resetPassword_unknownEmail_spendsOneHashOfWork() {
        when(userRepository.findByEmail("nadie@escuelaing.edu.co")).thenReturn(Optional.empty());

        assertThrows(InvalidResetCodeException.class, () -> service
                .resetPassword(reset("nadie@escuelaing.edu.co", CODE, NEW_PASSWORD, NEW_PASSWORD), CORR));

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

        /**
     * An inactive account spends a single work hash, like an incorrect code
     */
    @Test
    void resetPassword_inactiveAccount_spendsOneHashOfWork() {
        user.setState(UserState.INACTIVO);

        assertThrows(InvalidResetCodeException.class, () -> service.resetPassword(validReset(), CORR));

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

        /**
     * A user without a code spends a single work hash, like an incorrect code
     */
    @Test
    void resetPassword_userWithoutCode_spendsOneHashOfWork() {
        assertThrows(InvalidResetCodeException.class, () -> service.resetPassword(validReset(), CORR));

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

        /**
     * An expired code spends a single work hash, like an incorrect code
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
     * An incorrect code spends a single work hash, the one of the verification
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
     * A new password that breaks the policy is rejected with the message of form F-05, without looking up the code, and
     * is audited (alternate flow A2)
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
     * A different confirmation is rejected with the message of form F-05 and neither consumes nor counts the code
     * (alternate flow A2)
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
     * A code with an invalid format is rejected as invalid data with the message of form F-05
     */
    @Test
    void resetPassword_malformedCode_isRejectedAsInvalidData() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.resetPassword(reset(EMAIL, "12ab", NEW_PASSWORD, NEW_PASSWORD), CORR));

        assertEquals("Código incorrecto o vencido.", messageOf(error, "codigo"));
        verifyNoInteractions(userRepository, codeRepository);
    }

        /**
     * The missing fields are all reported at once
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
     * A field that the contract does not define is rejected (RG-04)
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
     * If the new password is equal to the current one and the code is correct it is rejected as invalid data without
     * consuming the code, and is audited (alternate flow A2)
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
     * The equality with the current password is only checked with a correct code: with an incorrect one the error is
     * the generic one and reveals nothing about the password
     */
    @Test
    void resetPassword_equalToTheCurrentWithAWrongCode_doesNotRevealAnythingAboutThePassword() {
        givenActiveCode();

        assertThrows(InvalidResetCodeException.class,
                () -> service.resetPassword(reset(EMAIL, "000000", OLD_PASSWORD, OLD_PASSWORD), CORR));
    }

        /**
     * If the audit fails the reset completes the same, the response does not change
     */
    @Test
    void resetPassword_whenTheAuditFails_stillCompletes() {
        givenActiveCode();
        doThrow(new IllegalStateException("MS08 down")).when(auditPublisher).publish(any(AuditEvent.class));

        assertDoesNotThrow(() -> service.resetPassword(validReset(), CORR));

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
    }

        /**
     * The audit event does not contain the password, the code or the hash
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
     * Without X-Correlation-ID the service generates a valid one for the event
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
     * The count of failed attempts must survive the rejection: the transaction is not rolled back with the invalid code
     * error, otherwise the attempt limit would be useless
     *
     * @throws NoSuchMethodException if the method of the service does not exist
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
     * A correct current password and a valid new one update the password with its hash and audit a successful
     * CAMBIO_CONTRASENA with the identity of the requester (base flow B)
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
     * Any role of the catalog can change their own password
     *
     * @param role role of the requester
     */
    @ParameterizedTest
    @MethodSource("edu.eci.dosw.campusseguro.identity.identity_service.SeedRoles#names")
    void changePassword_anyRole_isAccepted(String role) {
        RequesterIdentity identity = TestIdentities.of(userId, role);

        assertDoesNotThrow(() -> service.changePassword(identity, validChange(), CORR));

        assertEquals(role, singleAuditEvent().usuarioRoles());
    }

        /**
     * An incorrect current password is rejected with the message of form F-06 in its field, does not change the
     * password and is audited (alternate flow A3)
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
     * A new password equal to the current one is rejected with the message of form F-06 and is audited (alternate flow
     * A2)
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
     * A new password that breaks the policy is rejected with the message of form F-06, without querying the user, and
     * is audited (alternate flow A2)
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
     * A different confirmation is rejected with the message of form F-06 and is audited (alternate flow A2)
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
     * The missing fields are all reported at once
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
     * A field that the contract does not define, such as a user id, is rejected: the password that is changed is always
     * that of the user identified by the headers (RG-04)
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
     * Without the required permission 403 is answered without validating or querying anything, and the rejection is
     * audited
     */
    @Test
    void changePassword_withoutThePermission_isForbiddenAndAudited() {
        RequesterIdentity withoutRoles = TestIdentities.withPermissions(userId, Set.of());

        assertThrows(ForbiddenOperationException.class,
                () -> service.changePassword(withoutRoles, validChange(), CORR));

        verifyNoInteractions(userRepository);
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.CAMBIO_CONTRASENA, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("SIN_PERMISO", event.detalle());
    }

        /**
     * If the user of the token no longer exists 404 is answered
     */
    @Test
    void changePassword_userNoLongerExists_isNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.changePassword(requester(), validChange(), CORR));

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * If the audit fails the change completes the same, the response does not change
     */
    @Test
    void changePassword_whenTheAuditFails_stillCompletes() {
        doThrow(new IllegalStateException("MS08 down")).when(auditPublisher).publish(any(AuditEvent.class));

        assertDoesNotThrow(() -> service.changePassword(requester(), validChange(), CORR));

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
    }

        /**
     * No audit event of the change contains passwords or hashes
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
     * The password change neither sends emails nor touches the recovery codes
     */
    @Test
    void changePassword_doesNotTouchTheRecoveryCodesNorSendEmails() {
        service.changePassword(requester(), validChange(), CORR);

        verifyNoInteractions(codeRepository, emailService);
    }
}
