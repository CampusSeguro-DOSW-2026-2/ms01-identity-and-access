package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
import edu.eci.dosw.campusseguro.identity.identity_service.config.RegistrationPolicy;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResendRegistrationCodeRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.VerifyRegistrationRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PendingRegistrationEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidVerificationCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PendingRegistrationRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AfterCommitMailer;
import edu.eci.dosw.campusseguro.identity.identity_service.service.EmailService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RegistrationServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * Tests of the registration with verification of the email (RF-MS01-01, forms F-01 and F-14): pending registration,
 * verification of the code, resending, limits of the codes, audit and absence of sensitive data in the logs
 */
class RegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final List<String> DOMAINS = List.of("escuelaing.edu.co", "mail.escuelaing.edu.co");
    private static final RegistrationPolicy POLICY = new RegistrationPolicy(15, 5, 60, 5);
    private static final String CORR = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";
    private static final String EMAIL = "carlos.gomez@escuelaing.edu.co";
    private static final String PASSWORD = "MiPassword#2026";
    private static final String CODE = "482913";
    private static final String TOKEN = "token-de-registro-de-prueba";

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PendingRegistrationRepository pendingRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private AuditEventPublisher auditPublisher;
    private RegistrationServiceImpl service;

        /**
     * Prepares the service with simulated repositories, a real low-cost BCrypt encoder, a fixed database clock and an
     * executor that sends the emails in the same thread
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        pendingRepository = mock(PendingRegistrationRepository.class);
        emailService = mock(EmailService.class);
        auditPublisher = mock(AuditEventPublisher.class);
        passwordEncoder = spy(new BCryptPasswordEncoder(4));
        when(roleRepository.findByName("ESTUDIANTE"))
                .thenReturn(Optional.of(TestEntities.role("ESTUDIANTE", "ms01:profile:read")));
        when(pendingRepository.upsertWithNewCode(anyString(), anyString(), any(), anyString(), any(), anyString(),
                anyString(), anyString(), anyInt(), anyInt(), anyInt())).thenReturn(1);
        when(pendingRepository.replaceCode(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt())).thenReturn(1);
        service = serviceWith(DOMAINS, Runnable::run);
    }

        /**
     * Leaves clean the transaction synchronization that some tests activate
     */
    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

        /**
     * Builds the service with the indicated domains and executor of the emails, and leaves uncounted the invocations
     * that the constructor makes to the encoder
     *
     * @param domains institutional domains with which the service is configured
     * @param executor executor of the email sending
     * @return the configured service
     */
    private RegistrationServiceImpl serviceWith(List<String> domains, Executor executor) {
        RegistrationServiceImpl created = new RegistrationServiceImpl(userRepository, roleRepository,
                pendingRepository, passwordEncoder, emailService, new AfterCommitMailer(executor),
                new AuditRecorder(auditPublisher, Clock.fixed(NOW, ZoneOffset.UTC)),
                new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator()),
                FixedDatabaseTime.at(NOW), POLICY, domains);
        clearInvocations(passwordEncoder);
        return created;
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
     * Builds a verification request
     *
     * @param email email of the request
     * @param code code of the request
     * @return the verification request
     */
    private VerifyRegistrationRequest verification(String email, String code) {
        VerifyRegistrationRequest request = new VerifyRegistrationRequest();
        request.setEmail(email);
        request.setCode(code);
        request.setToken(TOKEN);
        return request;
    }

        /**
     * Builds a resending request
     *
     * @param email email of the request
     * @return the resending request
     */
    private ResendRegistrationCodeRequest resend(String email) {
        ResendRegistrationCodeRequest request = new ResendRegistrationCodeRequest();
        request.setEmail(email);
        request.setToken(TOKEN);
        return request;
    }

        /**
     * Registers in the simulated repository a pending registration of EMAIL with the code CODE
     *
     * @param expiresAt expiration of the code
     * @param attempts failed attempts already made
     * @return the pending registration
     */
    private PendingRegistrationEntity givenPending(Instant expiresAt, int attempts) {
        PendingRegistrationEntity pending = new PendingRegistrationEntity();
        pending.setEmail(EMAIL);
        pending.setFirstName("Carlos");
        pending.setSecondName(null);
        pending.setFirstLastName("Gomez");
        pending.setSecondLastName("Perez");
        pending.setPasswordHash("$2a$04$storedPasswordHash");
        pending.setCodeHash(passwordEncoder.encode(CODE));
        pending.setRequestTokenHash(sha256(TOKEN));
        pending.setAttempts(attempts);
        pending.setExpiresAt(expiresAt);
        pending.setCodeSentAt(NOW.minusSeconds(60));
        pending.setCodesSent(1);
        pending.setWindowStartedAt(NOW.minusSeconds(60));
        when(pendingRepository.findLockedByEmail(EMAIL)).thenReturn(Optional.of(pending));
        clearInvocations(passwordEncoder);
        return pending;
    }

        /**
     * Computes the SHA-256 hash of a token in lowercase hexadecimal, as the service stores it
     *
     * @param token token in clear text
     * @return the hash in hexadecimal
     */
    private static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

        /**
     * Makes the simulated user repository assign an id to the saved user
     *
     * @param id id that is assigned
     */
    private void givenSavedUsersGet(UUID id) {
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });
    }

        /**
     * Gets the code that was sent by email to EMAIL
     *
     * @return the code in clear text
     */
    private String sentCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendRegistrationCode(eq(EMAIL), code.capture(), eq(15));
        return code.getValue();
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
     * A valid registration is kept as pending with the hashes of the password and of the code and the values of the
     * policy, a code of six digits that matches the stored hash is sent, and no user is created
     */
    @Test
    void requestRegistration_validRequest_keepsThePendingRegistrationAndSendsTheCode() {
        String token = service.requestRegistration(registrationRequest("  Carlos.Gomez@EscuelaIng.edu.co  "), CORR);

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> codeHash = ArgumentCaptor.forClass(String.class);
        verify(pendingRepository).upsertWithNewCode(eq(EMAIL), eq("Carlos"), eq("Andres"), eq("Gomez"), eq("Perez"),
                passwordHash.capture(), codeHash.capture(), eq(sha256(token)), eq(15), eq(60), eq(5));

        String code = sentCode();
        assertTrue(code.matches("^[0-9]{6}$"));
        assertTrue(passwordEncoder.matches(code, codeHash.getValue()));
        assertTrue(passwordEncoder.matches(PASSWORD, passwordHash.getValue()));
        assertFalse(passwordHash.getValue().contains(PASSWORD));
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * A registration accepted for verification is not audited yet: the REGISTRO EXITO event is emitted when the user is
     * created
     */
    @Test
    void requestRegistration_validRequest_isNotAuditedYet() {
        service.requestRegistration(registrationRequest(EMAIL), CORR);

        verifyNoInteractions(auditPublisher);
    }

        /**
     * The blank optional names are kept as null
     */
    @Test
    void requestRegistration_blankOptionalNames_areKeptAsNull() {
        UserCreateRequest request = registrationRequest(EMAIL);
        request.setSecondName("   ");
        request.setSecondLastName("");

        service.requestRegistration(request, CORR);

        verify(pendingRepository).upsertWithNewCode(eq(EMAIL), eq("Carlos"), isNull(), eq("Gomez"), isNull(),
                anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt());
    }

        /**
     * If the minimum wait or the daily limit stop the overwrite, no code is sent and the request ends the same
     */
    @Test
    void requestRegistration_withinTheWaitOrTheDailyLimit_sendsNothingWithoutFailing() {
        when(pendingRepository.upsertWithNewCode(anyString(), anyString(), any(), anyString(), any(), anyString(),
                anyString(), anyString(), anyInt(), anyInt(), anyInt())).thenReturn(0);

        assertDoesNotThrow(() -> service.requestRegistration(registrationRequest(EMAIL), CORR));

        verifyNoInteractions(emailService);
    }

        /**
     * An email that already belongs to a user answers the conflict, is audited and nothing is kept or sent
     */
    @Test
    void requestRegistration_emailAlreadyExists_throwsAuditsAndKeepsNothing() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
                () -> service.requestRegistration(registrationRequest("Carlos.Gomez@escuelaing.edu.co"), CORR));

        verifyNoInteractions(pendingRepository, emailService);
        verify(passwordEncoder, never()).encode(any());
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CONFLICTO", event.detalle());
        assertNull(event.recursoAfectado());
    }

        /**
     * A domain outside the institution is rejected with the generic email message
     */
    @Test
    void requestRegistration_foreignDomain_isRejectedWithGenericEmailMessage() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.requestRegistration(registrationRequest("carlos.gomez@gmail.com"), CORR));

        assertEquals(1, error.getErrors().size());
        assertEquals("correo", error.getErrors().get(0).campo());
        assertEquals("Correo no válido.", error.getErrors().get(0).mensaje());
        verify(userRepository, never()).existsByEmail(any());
        verifyNoInteractions(pendingRepository, emailService);
    }

        /**
     * The email of each configured institutional domain is accepted
     *
     * @param email email with the institutional domain that is tested
     */
    @ParameterizedTest
    @ValueSource(strings = { "carlos.gomez@escuelaing.edu.co", "carlos.gomez@mail.escuelaing.edu.co" })
    void requestRegistration_emailOfEachConfiguredDomain_isAccepted(String email) {
        service.requestRegistration(registrationRequest(email), CORR);

        verify(pendingRepository).upsertWithNewCode(eq(email), anyString(), any(), anyString(), any(), anyString(),
                anyString(), anyString(), anyInt(), anyInt(), anyInt());
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
    void requestRegistration_domainNotInTheConfiguredList_isRejected(String email) {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.requestRegistration(registrationRequest(email), CORR));

        assertEquals("correo", error.getErrors().get(0).campo());
        assertEquals("Correo no válido.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(pendingRepository);
    }

        /**
     * The configured domains are normalized: spaces, uppercase and empty values are ignored
     */
    @Test
    void requestRegistration_configuredDomainsAreNormalized() {
        RegistrationServiceImpl normalized = serviceWith(List.of(" Mail.EscuelaIng.edu.co ", "", "   "),
                Runnable::run);

        normalized.requestRegistration(registrationRequest("carlos.gomez@mail.escuelaing.edu.co"), CORR);

        verify(pendingRepository).upsertWithNewCode(eq("carlos.gomez@mail.escuelaing.edu.co"), anyString(), any(),
                anyString(), any(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt());
        assertThrows(InvalidRequestException.class,
                () -> normalized.requestRegistration(registrationRequest(EMAIL), CORR));
    }

        /**
     * Invalid data is audited as a failure and all the fields are reported
     */
    @Test
    void requestRegistration_invalidData_auditsFalloAndReportsEveryField() {
        UserCreateRequest request = registrationRequest("no-es-un-correo");
        request.setFirstName("A");
        request.setPasswordConfirmation("Otra#Clave2026");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.requestRegistration(request, CORR));

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
        verifyNoInteractions(pendingRepository);
    }

        /**
     * The email is only sent after the transaction is committed, a pending registration that was not stored never
     * receives a code
     */
    @Test
    void requestRegistration_insideATransaction_sendsTheEmailOnlyAfterTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.requestRegistration(registrationRequest(EMAIL), CORR);

        verifyNoInteractions(emailService);
        TransactionSynchronizationUtils.triggerAfterCommit();
        assertNotNull(sentCode());
    }

        /**
     * If the transaction is rolled back the email is never sent
     */
    @Test
    void requestRegistration_transactionRolledBack_neverSendsTheEmail() {
        TransactionSynchronizationManager.initSynchronization();

        service.requestRegistration(registrationRequest(EMAIL), CORR);
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(emailService);
    }

        /**
     * If the mail server fails the request ends the same and the response does not change
     */
    @Test
    void requestRegistration_whenTheMailServerFails_stillCompletes() {
        doThrow(new MailSendException("SMTP down")).when(emailService)
                .sendRegistrationCode(anyString(), anyString(), anyInt());

        assertDoesNotThrow(() -> service.requestRegistration(registrationRequest(EMAIL), CORR));
    }

        /**
     * A correct code creates the user with the data and the password of the pending registration, in ACTIVO status and
     * with the ESTUDIANTE role, deletes the pending registration and answers the created user
     */
    @Test
    void verify_correctCode_createsTheUserAndDeletesThePendingRegistration() {
        UUID id = UUID.randomUUID();
        PendingRegistrationEntity pending = givenPending(NOW.plusSeconds(600), 0);
        givenSavedUsersGet(id);

        UserResponse response = service.verify(verification("  Carlos.Gomez@EscuelaIng.edu.co ", CODE), CORR);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserEntity saved = captor.getValue();
        assertEquals(EMAIL, saved.getEmail());
        assertEquals("Carlos", saved.getFirstName());
        assertNull(saved.getSecondName());
        assertEquals("Gomez", saved.getFirstLastName());
        assertEquals("Perez", saved.getSecondLastName());
        assertEquals("$2a$04$storedPasswordHash", saved.getPassword());
        assertEquals(List.of("ESTUDIANTE"), saved.getRoleNames());
        assertEquals(UserState.ACTIVO, saved.getState());
        verify(pendingRepository).delete(pending);

        assertEquals(id, response.id());
        assertEquals(EMAIL, response.email());
        assertEquals(List.of("ESTUDIANTE"), response.roles());
        assertEquals(UserState.ACTIVO, response.state());
    }

        /**
     * The creation of the user is audited without personal data
     */
    @Test
    void verify_correctCode_auditsRegistroExitoWithoutPersonalData() {
        UUID id = UUID.randomUUID();
        givenPending(NOW.plusSeconds(600), 0);
        givenSavedUsersGet(id);

        service.verify(verification(EMAIL, CODE), CORR);

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals("usuario:" + id, event.recursoAfectado());
        assertEquals(CORR, event.correlationId());
        assertNull(event.usuarioId());
        assertNull(event.usuarioRoles());
        assertNull(event.detalle());
    }

        /**
     * A wrong code is rejected with the generic error, counts the attempt, is audited and creates nothing
     */
    @Test
    void verify_wrongCode_rejectsCountsTheAttemptAndAudits() {
        PendingRegistrationEntity pending = givenPending(NOW.plusSeconds(600), 0);

        assertThrows(InvalidVerificationCodeException.class,
                () -> service.verify(verification(EMAIL, "000000"), CORR));

        assertEquals(1, pending.getAttempts());
        assertEquals(NOW.plusSeconds(600), pending.getExpiresAt());
        verify(pendingRepository).saveAndFlush(pending);
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        verify(pendingRepository, never()).delete(any());
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CODIGO_INVALIDO", event.detalle());
        assertNull(event.recursoAfectado());
    }

        /**
     * The last allowed wrong attempt invalidates the code: its expiration moves to the present, the row is kept for the
     * daily limit, and the right code is rejected afterwards
     */
    @Test
    void verify_lastAllowedWrongAttempt_invalidatesTheCode() {
        PendingRegistrationEntity pending = givenPending(NOW.plusSeconds(600), 4);

        assertThrows(InvalidVerificationCodeException.class,
                () -> service.verify(verification(EMAIL, "000000"), CORR));

        assertEquals(5, pending.getAttempts());
        assertEquals(NOW, pending.getExpiresAt());
        verify(pendingRepository, never()).delete(any());
        assertThrows(InvalidVerificationCodeException.class, () -> service.verify(verification(EMAIL, CODE), CORR));
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * An expired code is rejected even if it is the right one
     */
    @Test
    void verify_expiredCode_isRejectedEvenIfItIsTheRightOne() {
        givenPending(NOW.minusSeconds(1), 0);

        assertThrows(InvalidVerificationCodeException.class, () -> service.verify(verification(EMAIL, CODE), CORR));

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * An email without a pending registration is rejected with the same error, spending the same hashing time as a
     * wrong code
     */
    @Test
    void verify_withoutPendingRegistration_isRejectedWithTheSameErrorAndTime() {
        when(pendingRepository.findLockedByEmail(EMAIL)).thenReturn(Optional.empty());

        InvalidVerificationCodeException error = assertThrows(InvalidVerificationCodeException.class,
                () -> service.verify(verification(EMAIL, CODE), CORR));

        assertEquals("Código incorrecto o vencido.", error.getMessage());
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        assertEquals("CODIGO_INVALIDO", singleAuditEvent().detalle());
    }

        /**
     * If the email became a user while the registration was pending, the conflict is answered and audited
     */
    @Test
    void verify_emailAlreadyRegistered_throwsTheConflict() {
        givenPending(NOW.plusSeconds(600), 0);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> service.verify(verification(EMAIL, CODE), CORR));

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        assertEquals("CONFLICTO", singleAuditEvent().detalle());
    }

        /**
     * A violation of the uniqueness constraint is reported as an email conflict
     */
    @Test
    void verify_uniqueConstraintViolation_isReportedAsEmailConflict() {
        givenPending(NOW.plusSeconds(600), 0);
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThrows(EmailAlreadyExistsException.class, () -> service.verify(verification(EMAIL, CODE), CORR));

        assertEquals("CONFLICTO", singleAuditEvent().detalle());
        verify(pendingRepository, never()).delete(any());
    }

        /**
     * If the ESTUDIANTE system role does not exist the verification fails without creating anything
     */
    @Test
    void verify_studentRoleMissingInTheDatabase_failsWithoutSaving() {
        givenPending(NOW.plusSeconds(600), 0);
        when(roleRepository.findByName("ESTUDIANTE")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.verify(verification(EMAIL, CODE), CORR));

        verify(userRepository, never()).saveAndFlush(any());
        verify(pendingRepository, never()).delete(any());
    }

        /**
     * Invalid data is rejected with the messages of form F-14 and audited, without looking for the registration
     */
    @Test
    void verify_invalidData_isRejectedWithTheFormMessagesAndAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.verify(verification("no-es-un-correo", "12ab"), CORR));

        assertEquals(List.of("codigo", "correo"), error.getErrors().stream()
                .map(ErrorResponse.ErrorDetail::campo).toList());
        assertEquals("Código incorrecto o vencido.", error.getErrors().get(0).mensaje());
        assertEquals("Ingrese un correo válido.", error.getErrors().get(1).mensaje());
        assertEquals("VALIDACION", singleAuditEvent().detalle());
        verifyNoInteractions(pendingRepository);
    }

        /**
     * The verification is transactional and keeps the failed attempts when the code is rejected
     *
     * @throws NoSuchMethodException if the method does not exist
     */
    @Test
    void verify_isTransactionalAndKeepsTheFailedAttemptsWhenTheCodeIsRejected() throws NoSuchMethodException {
        Method method = RegistrationServiceImpl.class.getMethod("verify", VerifyRegistrationRequest.class,
                String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(List.of(InvalidVerificationCodeException.class), List.of(transactional.noRollbackFor()));
    }

        /**
     * A failure of the audit does not change the response of the verification
     */
    @Test
    void verify_auditFailure_doesNotChangeTheResponse() {
        UUID id = UUID.randomUUID();
        givenPending(NOW.plusSeconds(600), 0);
        givenSavedUsersGet(id);
        doThrow(new IllegalStateException("MS08 down")).when(auditPublisher).publish(any(AuditEvent.class));

        assertEquals(id, service.verify(verification(EMAIL, CODE), CORR).id());
    }

        /**
     * The resending gives a new code to the pending registration with the values of the policy and sends it
     */
    @Test
    void resendCode_pendingRegistration_replacesTheCodeAndSendsIt() {
        service.resendCode(resend(" Carlos.Gomez@EscuelaIng.edu.co "), CORR);

        ArgumentCaptor<String> codeHash = ArgumentCaptor.forClass(String.class);
        verify(pendingRepository).replaceCode(eq(EMAIL), eq(sha256(TOKEN)), codeHash.capture(), eq(15), eq(60), eq(5));
        assertTrue(passwordEncoder.matches(sentCode(), codeHash.getValue()));
    }

        /**
     * Without a pending registration, within the wait or at the daily limit nothing is sent and the request ends the
     * same
     */
    @Test
    void resendCode_nothingReplaced_sendsNothingWithoutFailing() {
        when(pendingRepository.replaceCode(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt())).thenReturn(0);

        assertDoesNotThrow(() -> service.resendCode(resend(EMAIL), CORR));

        verifyNoInteractions(emailService);
    }

        /**
     * The resending always spends the hashing time of a code, whether or not the registration exists
     */
    @Test
    void resendCode_alwaysSpendsOneHashOfWork() {
        when(pendingRepository.replaceCode(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt())).thenReturn(0);

        service.resendCode(resend(EMAIL), CORR);

        verify(passwordEncoder, times(1)).encode(anyString());
    }

        /**
     * An invalid email is rejected with the message of form F-14 without touching the registrations, and the resending
     * is never audited
     */
    @Test
    void resendCode_invalidEmail_isRejectedAndNothingIsAudited() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> service.resendCode(resend("no-es-un-correo"), CORR));

        assertEquals("Ingrese un correo válido.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(pendingRepository, auditPublisher);
        service.resendCode(resend(EMAIL), CORR);
        verifyNoInteractions(auditPublisher);
    }

        /**
     * The code, the password and the email are never written to the logs, neither when the sending fails nor when a code
     * is rejected
     */
    @Test
    void registrationFlow_neverWritesTheCodeThePasswordOrTheEmailToTheLogs() {
        List<ListAppender<ILoggingEvent>> appenders = new ArrayList<>();
        List<Logger> loggers = List.of((Logger) LoggerFactory.getLogger(RegistrationServiceImpl.class),
                (Logger) LoggerFactory.getLogger(AfterCommitMailer.class));
        for (Logger logger : loggers) {
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            appenders.add(appender);
        }
        try {
            doThrow(new MailSendException("550 5.1.1 <" + EMAIL + "> rejected")).when(emailService)
                    .sendRegistrationCode(anyString(), anyString(), anyInt());
            service.requestRegistration(registrationRequest(EMAIL), CORR);
            service.resendCode(resend(EMAIL), CORR);
            givenPending(NOW.plusSeconds(600), 0);
            assertThrows(InvalidVerificationCodeException.class,
                    () -> service.verify(verification(EMAIL, "000000"), CORR));

            List<String> messages = new ArrayList<>();
            for (ListAppender<ILoggingEvent> appender : appenders) {
                for (ILoggingEvent event : appender.list) {
                    messages.add(event.getFormattedMessage());
                }
            }
            assertFalse(messages.isEmpty());
            for (String message : messages) {
                assertFalse(message.contains(EMAIL));
                assertFalse(message.contains(CODE));
                assertFalse(message.contains("000000"));
                assertFalse(message.contains(PASSWORD));
            }
        } finally {
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).detachAppender(appenders.get(i));
            }
        }
    }

        /**
     * The registration returns a random token of 43 URL-safe characters, different every time, and only its hash is
     * stored (RN-MS01-46)
     */
    @Test
    void requestRegistration_returnsARandomTokenAndStoresOnlyItsHash() {
        String first = service.requestRegistration(registrationRequest(EMAIL), CORR);
        String second = service.requestRegistration(registrationRequest(EMAIL), CORR);

        assertTrue(first.matches("^[A-Za-z0-9_-]{43}$"));
        assertFalse(first.equals(second));
        ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
        verify(pendingRepository, times(2)).upsertWithNewCode(anyString(), anyString(), any(), anyString(), any(),
                anyString(), anyString(), tokenHash.capture(), anyInt(), anyInt(), anyInt());
        assertEquals(List.of(sha256(first), sha256(second)), tokenHash.getAllValues());
        assertFalse(tokenHash.getAllValues().contains(first));
    }

        /**
     * Within the wait or at the daily limit a token is returned anyway, so the response does not reveal it; that token
     * was never stored and does not confirm anything
     */
    @Test
    void requestRegistration_withinTheWaitOrTheDailyLimit_stillReturnsAToken() {
        when(pendingRepository.upsertWithNewCode(anyString(), anyString(), any(), anyString(), any(), anyString(),
                anyString(), anyString(), anyInt(), anyInt(), anyInt())).thenReturn(0);

        assertTrue(service.requestRegistration(registrationRequest(EMAIL), CORR).matches("^[A-Za-z0-9_-]{43}$"));
    }

        /**
     * The right code with the token of another registration of the same email is rejected with the generic error,
     * creates nothing and does not spend the attempts of the owner (RN-MS01-46): someone who registered the same email
     * later cannot make the owner confirm an account with a password of someone else
     */
    @Test
    void verify_tokenOfAnotherRegistration_isRejectedWithoutCountingTheAttempt() {
        PendingRegistrationEntity pending = givenPending(NOW.plusSeconds(600), 0);
        pending.setRequestTokenHash(sha256("token-de-otra-persona"));

        assertThrows(InvalidVerificationCodeException.class, () -> service.verify(verification(EMAIL, CODE), CORR));

        assertEquals(0, pending.getAttempts());
        verify(pendingRepository, never()).saveAndFlush(any());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        assertEquals("CODIGO_INVALIDO", singleAuditEvent().detalle());
    }

        /**
     * The verification without the token is invalid data, with the generic message of the code
     */
    @Test
    void verify_withoutToken_isRejectedAsInvalidData() {
        VerifyRegistrationRequest request = verification(EMAIL, CODE);
        request.setToken("   ");

        InvalidRequestException error = assertThrows(InvalidRequestException.class, () -> service.verify(request, CORR));

        assertEquals("token_registro", error.getErrors().get(0).campo());
        assertEquals("Código incorrecto o vencido.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(pendingRepository);
    }

        /**
     * The resending without the token is invalid data and touches nothing
     */
    @Test
    void resendCode_withoutToken_isRejectedAsInvalidData() {
        ResendRegistrationCodeRequest request = resend(EMAIL);
        request.setToken(null);

        assertThrows(InvalidRequestException.class, () -> service.resendCode(request, CORR));

        verifyNoInteractions(pendingRepository, emailService);
    }
}
