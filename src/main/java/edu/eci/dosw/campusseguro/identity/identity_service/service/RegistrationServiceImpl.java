package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.config.RegistrationPolicy;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResendRegistrationCodeRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.VerifyRegistrationRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PendingRegistrationEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidVerificationCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PendingRegistrationRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * Registration of institutional users with verification of the email (RF-MS01-01)
 *
 * The form is kept as a pending registration (table pending_registrations) and a code of six digits is sent to the
 * email, the user is only created in users when that code is confirmed, so the emails that do not exist or do not
 * belong to whoever registers never become users
 *
 * RN-MS01-43: The user is only created when the code sent to the email is confirmed, a new registration of the same
 * email replaces the pending one
 * RN-MS01-46: Only whoever made the registration can confirm it: the registration returns a random token, stored only
 * as its SHA-256 hash, that the verification and the resending require together with the code. If someone else
 * registers the same email later, the token of the first one stops being valid, so the code that the owner of the email
 * receives can never create an account with the password of someone else
 * RN-MS01-44: The code is stored as a hash, it is valid for a short and configurable time, it is invalidated after a
 * maximum number of failed attempts, a minimum time must pass between two codes of the same email and there is a daily
 * limit of codes per email
 *
 * RNF-MS01-10: Every rejected attempt and the created user are audited with the REGISTRO action, the audit never
 * includes passwords, hashes, codes or emails and a failure when auditing does not change the response
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    private static final int CODE_BOUND = 1_000_000;
    private static final int TOKEN_BYTES = 32;
    private static final String CODE_DESCRIPTION = "registration verification code";

    static final String FIELD_EMAIL = "correo";

    static final String REASON_VALIDATION = "VALIDACION";
    static final String REASON_CONFLICT = "CONFLICTO";
    static final String REASON_INVALID_CODE = "CODIGO_INVALIDO";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PendingRegistrationRepository pendingRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AfterCommitMailer mailer;
    private final AuditRecorder auditRecorder;
    private final RequestValidator requestValidator;
    private final DatabaseTimeService timeService;
    private final RegistrationPolicy policy;
    private final Set<String> institutionalDomains;
    private final SecureRandom secureRandom = new SecureRandom();

        /**
     * For security a padding password and hash are used against which a hash is computed when the email has no active
     * pending registration, this equalizes the response time with the one of a wrong code
     */
    private final String dummyPassword = UUID.randomUUID().toString();
    private final String dummyHash;

        /**
     * Creates the service with its dependencies
     *
     * @param userRepository repository to access the users
     * @param roleRepository repository to access the roles
     * @param pendingRepository repository of the pending registrations
     * @param passwordEncoder encoder with which the passwords and the codes are stored and verified
     * @param emailService service that sends the code by email
     * @param mailer sender of the email after the commit and outside the request thread
     * @param auditRecorder recorder of the audit events of MS08
     * @param requestValidator validator of the request bodies
     * @param timeService single database clock with which the validity of the codes is evaluated
     * @param policy validity, attempts, wait and daily limit of the codes
     * @param institutionalDomains configured institutional email domains, without the at sign, spaces, uppercase and
     *        empty values are ignored
     */
    public RegistrationServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
            PendingRegistrationRepository pendingRepository, PasswordEncoder passwordEncoder,
            EmailService emailService, AfterCommitMailer mailer, AuditRecorder auditRecorder,
            RequestValidator requestValidator, DatabaseTimeService timeService, RegistrationPolicy policy,
            @Value("${identity.institutional-domains:escuelaing.edu.co,mail.escuelaing.edu.co}")
            List<String> institutionalDomains) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.pendingRepository = pendingRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.mailer = mailer;
        this.auditRecorder = auditRecorder;
        this.requestValidator = requestValidator;
        this.timeService = timeService;
        this.policy = policy;
        this.institutionalDomains = institutionalDomains.stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.dummyHash = passwordEncoder.encode(dummyPassword);
    }

        /**
     * Keeps the registration as pending and sends the verification code (RF-MS01-01 steps 1 to 5), a rejected attempt
     * is audited in MS08
     *
     * If the minimum wait since the last code of the email has not passed or the email reached the daily limit of
     * codes, nothing is written or sent and the response is the same
     *
     * The response is the same in every case: when nothing is written the token returned is not valid, so the
     * response does not reveal whether the wait or the daily limit stopped the request
     *
     * @param request data of the registration form
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the registration token that only whoever made this registration receives
     * @throws InvalidRequestException if the data breaks form F-01
     * @throws EmailAlreadyExistsException if the email already belongs to a user
     */
    @Override
    @Transactional
    public String requestRegistration(UserCreateRequest request, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        List<ErrorResponse.ErrorDetail> errors = collectRegistrationErrors(request);
        if (!errors.isEmpty()) {
            auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.FALLO, null, null, REASON_VALIDATION,
                    corr);
            log.warn("Registration rejected because of invalid data");
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, errors);
        }

        String email = request.getEmail();
        if (userRepository.existsByEmail(email)) {
            throw emailConflict(corr);
        }

        String code = generateCode();
        String token = generateToken();
        int written = pendingRepository.upsertWithNewCode(email, request.getFirstName(), request.getSecondName(),
                request.getFirstLastName(), request.getSecondLastName(),
                passwordEncoder.encode(request.getPassword()), passwordEncoder.encode(code), hashToken(token),
                policy.codeTtlMinutes(), policy.resendCooldownSeconds(), policy.maxCodesPerDay());
        if (written == 0) {
            log.info("Registration code not sent: minimum wait or daily limit of the email");
            return token;
        }

        mailer.sendAfterCommit(CODE_DESCRIPTION,
                () -> emailService.sendRegistrationCode(email, code, policy.codeTtlMinutes()), corr);
        log.info("Registration pending of verification, code generated");
        return token;
    }

        /**
     * Confirms the code and creates the user in ACTIVO status with the ESTUDIANTE role (RF-MS01-01 steps 6 to 9),
     * every attempt, successful or failed, is audited in MS08
     *
     * The token is checked before the code and a wrong token does not count as a failed attempt, so whoever does not
     * have it can neither confirm the registration nor spend the attempts of its owner. The failed attempts are kept
     * even though the request is rejected, so they are not rolled back with the exception
     *
     * @param request email and code
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the created user with its id, email, roles and status
     * @throws InvalidRequestException if the data breaks form F-14
     * @throws InvalidVerificationCodeException if there is no pending registration or the code is incorrect or expired
     * @throws EmailAlreadyExistsException if the email became a user while the registration was pending
     */
    @Override
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public UserResponse verify(VerifyRegistrationRequest request, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        List<ErrorResponse.ErrorDetail> errors = requestValidator.collectErrors(request);
        if (!errors.isEmpty()) {
            auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.FALLO, null, null, REASON_VALIDATION,
                    corr);
            log.warn("Registration verification rejected because of invalid data");
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, errors);
        }

        Optional<PendingRegistrationEntity> candidate = pendingRepository.findLockedByEmail(request.getEmail());
        Instant now = timeService.now();
        if (candidate.isEmpty() || !candidate.get().isCodeActiveAt(now)) {
            spendHashingTime();
            throw rejectedCode(corr);
        }

        PendingRegistrationEntity pending = candidate.get();
        if (!tokenMatches(request.getToken(), pending.getRequestTokenHash())) {
            spendHashingTime();
            throw rejectedCode(corr);
        }
        if (!passwordEncoder.matches(request.getCode(), pending.getCodeHash())) {
            registerFailedAttempt(pending, now);
            throw rejectedCode(corr);
        }

        if (userRepository.existsByEmail(pending.getEmail())) {
            throw emailConflict(corr);
        }

        UserEntity entity = new UserEntity();
        entity.setFirstName(pending.getFirstName());
        entity.setSecondName(pending.getSecondName());
        entity.setFirstLastName(pending.getFirstLastName());
        entity.setSecondLastName(pending.getSecondLastName());
        entity.setEmail(pending.getEmail());
        entity.setPassword(pending.getPasswordHash());
        RoleEntity studentRole = roleRepository.findByName(RoleNames.STUDENT).orElseThrow(
                () -> new IllegalStateException("The system role " + RoleNames.STUDENT + " does not exist"));
        entity.setRoles(Set.of(studentRole));
        entity.setState(UserState.ACTIVO);

        UserEntity saved;
        try {
            saved = userRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            log.warn("Registration rejected by a uniqueness constraint: {}", e.getClass().getSimpleName());
            throw emailConflict(corr);
        }
        pendingRepository.delete(pending);

        auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.EXITO, null,
                AuditRecorder.userResource(saved.getId()), null, corr);
        log.info("Usuario registrado. usuario={}", saved.getId());
        return UserResponse.fromEntity(saved);
    }

        /**
     * Sends a new code to an email with a pending registration (RF-MS01-01 alternate flow A1), only to whoever made it
     * (same registration token), the data of the registration does not change and the response is the same whether or
     * not the code is sent
     *
     * @param request email of the pending registration
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs
     * @throws InvalidRequestException if the email breaks form F-14
     */
    @Override
    @Transactional
    public void resendCode(ResendRegistrationCodeRequest request, String correlationId) {
        requestValidator.validate(request);
        String corr = CorrelationIds.resolve(correlationId);

        String email = request.getEmail();
        String code = generateCode();
        int written = pendingRepository.replaceCode(email, hashToken(request.getToken()), passwordEncoder.encode(code),
                policy.codeTtlMinutes(), policy.resendCooldownSeconds(), policy.maxCodesPerDay());
        if (written == 0) {
            log.info("Registration code not resent: no pending registration, minimum wait or daily limit");
            return;
        }

        mailer.sendAfterCommit(CODE_DESCRIPTION,
                () -> emailService.sendRegistrationCode(email, code, policy.codeTtlMinutes()), corr);
        log.info("Registration code generated again");
    }

        /**
     * Gathers the errors of the registration form: those of format and rules of each field, and that the email belongs
     * to the institutional domain, without indicating whether the email failed because of the domain or the format
     *
     * @param request data of the registration form
     * @return errors by field, empty if the form is valid
     */
    private List<ErrorResponse.ErrorDetail> collectRegistrationErrors(UserCreateRequest request) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>(requestValidator.collectErrors(request));
        boolean emailAlreadyInvalid = false;
        for (ErrorResponse.ErrorDetail error : errors) {
            if (FIELD_EMAIL.equals(error.campo())) {
                emailAlreadyInvalid = true;
            }
        }
        if (!emailAlreadyInvalid && !belongsToInstitutionalDomain(request.getEmail())) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_EMAIL, FieldRules.EMAIL_REGISTER_MESSAGE));
        }
        return ValidationErrors.merge(errors);
    }

        /**
     * Tells whether the email belongs to any of the configured institutional domains
     *
     * @param email email already normalized to lowercase
     * @return true if the part after the at sign is equal to any of the institutional domains
     */
    private boolean belongsToInstitutionalDomain(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return false;
        }
        return institutionalDomains.contains(email.substring(at + 1));
    }

        /**
     * Generates a random numeric code of six digits with leading zeros
     *
     * @return the code in clear text
     */
    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_BOUND));
    }

        /**
     * Generates the registration token: 32 random bytes in URL-safe Base64 without padding
     *
     * @return the token in clear text, it is only sent in the response of the registration
     */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

        /**
     * Computes the SHA-256 hash of a registration token, in lowercase hexadecimal; a token of high entropy does not
     * need a slow hash like the password
     *
     * @param token token in clear text
     * @return the 64 hexadecimal characters of the hash
     */
    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

        /**
     * Compares the received token with the stored hash in constant time
     *
     * @param token token received from the client
     * @param storedHash hash stored in the pending registration
     * @return true if the token corresponds to the hash
     */
    private static boolean tokenMatches(String token, String storedHash) {
        byte[] received = hashToken(token).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(received, storedHash.getBytes(StandardCharsets.US_ASCII));
    }

        /**
     * Counts a failed attempt of the code, when the maximum is reached the code is invalidated by moving its expiration
     * to the present, the row is kept so the daily limit of the email is not reset
     *
     * @param pending pending registration locked by the transaction
     * @param now current instant of the database
     */
    private void registerFailedAttempt(PendingRegistrationEntity pending, Instant now) {
        int attempts = pending.getAttempts() + 1;
        pending.setAttempts(attempts);
        if (attempts >= policy.maxAttempts()) {
            pending.setExpiresAt(now);
            log.warn("Registration code invalidated for exceeding the allowed attempts");
        }
        pendingRepository.saveAndFlush(pending);
    }

        /**
     * Computes a padding hash to equalize the time of the branches that do not verify a code
     */
    private void spendHashingTime() {
        passwordEncoder.matches(dummyPassword, dummyHash);
    }

        /**
     * Audits the rejection of a code and creates the exception that is thrown to the client
     *
     * @param corr correlation id of the request
     * @return the exception of an incorrect or expired code
     */
    private InvalidVerificationCodeException rejectedCode(String corr) {
        auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.FALLO, null, null, REASON_INVALID_CODE,
                corr);
        log.warn("Registration verification rejected. reason={}", REASON_INVALID_CODE);
        return new InvalidVerificationCodeException();
    }

        /**
     * Audits the rejection for a duplicated email and creates the exception that is thrown to the client
     *
     * @param corr correlation id of the request
     * @return the exception of an already registered email
     */
    private EmailAlreadyExistsException emailConflict(String corr) {
        auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.FALLO, null, null, REASON_CONFLICT, corr);
        log.warn("Registration rejected: email already registered");
        return new EmailAlreadyExistsException();
    }
}
