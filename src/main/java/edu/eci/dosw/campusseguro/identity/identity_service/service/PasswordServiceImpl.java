package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.config.RecoveryPolicy;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PasswordResetCodeEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidResetCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PasswordResetCodeRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * RF-MS01-08 Password recovery and change
 *
 * Base flow A (reset): request the code -> generate a 6-digit code, store only its hash and send it by email ->
 * validate email, code and new password -> update the password, invalidate the code and audit RECUPERACION
 *
 * Base flow B (change): validate the current password, the policy and that the new one is different from the current
 * one -> update the password and audit CAMBIO_CONTRASENA
 *
 * RN-MS01-20: The response to the code request does not reveal whether the email exists, neither by its content nor by
 * the response time: the email is sent outside the request thread and the branches that do not generate a code spend
 * the same hashing time as the one that does generate it
 * RN-MS01-21: The code is single-use, has a short and configurable validity, and there is only one valid code per user,
 * in addition it is invalidated after a maximum number of failed attempts and a minimum time must pass between two
 * requests
 * RN-MS01-22: The new password meets the policy and is stored as a BCrypt hash
 *
 * RNF-MS01-10: The reset and the change are audited, successful and failed, the audit never includes passwords, hashes,
 * codes or emails and a failure when auditing does not change the response
 */
@Service
public class PasswordServiceImpl implements PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordServiceImpl.class);

    private static final int CODE_BOUND = 1_000_000;

    static final String REASON_VALIDATION = "VALIDACION";
    static final String REASON_INVALID_CODE = "CODIGO_INVALIDO";
    static final String REASON_SAME_AS_CURRENT = "NUEVA_IGUAL_A_ACTUAL";
    static final String REASON_WRONG_CURRENT = "CONTRASENA_ACTUAL_INCORRECTA";
    static final String REASON_NOT_ALLOWED = "SIN_PERMISO";

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditRecorder auditRecorder;
    private final RequestValidator requestValidator;
    private final Clock clock;
    private final RecoveryPolicy policy;
    private final AfterCommitMailer mailer;
    private final SecureRandom secureRandom = new SecureRandom();

        /**
     * For security a padding password and hash are used against which a hash is computed when the request does not
     * generate a code or the received code does not correspond to any account, this equalizes the response time of all
     * the branches and avoids revealing through timing whether an email is registered
     */
    private final String dummyPassword = UUID.randomUUID().toString();
    private final String dummyHash;

        /**
     * Creates the service with its dependencies
     *
     * @param userRepository repository to access the users
     * @param codeRepository repository to access the recovery codes
     * @param passwordEncoder encoder with which the passwords and the codes are stored and verified
     * @param emailService service that sends the code by email
     * @param auditRecorder recorder of the audit events of MS08
     * @param requestValidator validator of the request bodies
     * @param clock UTC clock with which the codes are dated and expire
     * @param policy validity, maximum attempts and wait between requests of the codes
     * @param mailer sender of the email after the commit and outside the request thread
     */
    public PasswordServiceImpl(UserRepository userRepository, PasswordResetCodeRepository codeRepository,
            PasswordEncoder passwordEncoder, EmailService emailService, AuditRecorder auditRecorder,
            RequestValidator requestValidator, Clock clock, RecoveryPolicy policy,
            AfterCommitMailer mailer) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditRecorder = auditRecorder;
        this.requestValidator = requestValidator;
        this.clock = clock;
        this.policy = policy;
        this.mailer = mailer;
        this.dummyHash = passwordEncoder.encode(dummyPassword);
    }

        /**
     * Requests the password recovery code (RF-MS01-08, flow A), the code is generated and sent only if the email
     * corresponds to an active account and the wait since the previous code has passed, but the response is the same in
     * all cases (RN-MS01-20)
     *
     * @param request email of the account to be recovered
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @throws InvalidRequestException if the email breaks form F-04
     */
    @Override
    @Transactional
    public void requestRecoveryCode(ForgotPasswordRequest request, String correlationId) {
        requestValidator.validate(request);
        String corr = CorrelationIds.resolve(correlationId);

        Optional<UserEntity> candidate = userRepository.findByEmail(request.getEmail());
        if (candidate.isEmpty()) {
            spendHashingTime();
            log.info("Recovery code request without an associated account");
            return;
        }

        UserEntity user = candidate.get();
        if (user.getState() != UserState.ACTIVO) {
            spendHashingTime();
            log.info("Recovery code request of an inactive account. user={}", user.getId());
            return;
        }

        Instant now = clock.instant();
        if (isWithinCooldown(user.getId(), now)) {
            spendHashingTime();
            log.info("Recovery code request within the minimum wait. user={}", user.getId());
            return;
        }

        issueCode(user, now, corr);
    }

        /**
     * Resets the password with the code received by email (RF-MS01-08, flow A), the code is single-use and every
     * attempt, successful or failed, is audited, the failed attempts with a valid code are counted even if the request
     * is rejected
     *
     * @param request email, code and new password with its confirmation
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @throws InvalidRequestException if the data breaks form F-05 or the new password is equal to the current one
     * @throws InvalidResetCodeException if the code is incorrect, expired or already used
     */
    @Override
    @Transactional(noRollbackFor = InvalidResetCodeException.class)
    public void resetPassword(ResetPasswordRequest request, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        List<ErrorResponse.ErrorDetail> errors = requestValidator.collectErrors(request);
        if (!errors.isEmpty()) {
            auditRecorder.recordAnonymous(AuditAction.RECUPERACION, AuditResult.FALLO, null, null,
                    REASON_VALIDATION, corr);
            log.warn("Recovery rejected because of invalid data");
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, errors);
        }

        Optional<UserEntity> candidate = userRepository.findByEmail(request.getEmail());
        if (candidate.isEmpty()) {
            spendHashingTime();
            throw rejectedCode(corr, null);
        }

        UserEntity user = candidate.get();
        if (user.getState() != UserState.ACTIVO) {
            spendHashingTime();
            throw rejectedCode(corr, user.getId());
        }

        Optional<PasswordResetCodeEntity> latest = codeRepository
                .findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId());
        Instant now = clock.instant();
        if (latest.isEmpty() || !latest.get().isActiveAt(now)) {
            spendHashingTime();
            throw rejectedCode(corr, user.getId());
        }

        PasswordResetCodeEntity code = latest.get();
        if (!passwordEncoder.matches(request.getCode(), code.getCodeHash())) {
            registerFailedAttempt(code, now);
            throw rejectedCode(corr, user.getId());
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            auditRecorder.recordAnonymous(AuditAction.RECUPERACION, AuditResult.FALLO, user.getId(),
                    AuditRecorder.userResource(user.getId()), REASON_SAME_AS_CURRENT, corr);
            log.warn("Recovery rejected: the new password is equal to the current one. user={}",
                    user.getId());
            throw sameAsCurrent();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);

        code.setUsedAt(now);
        codeRepository.saveAndFlush(code);
        codeRepository.invalidateUnused(user.getId(), now);

        auditRecorder.recordAnonymous(AuditAction.RECUPERACION, AuditResult.EXITO, user.getId(),
                AuditRecorder.userResource(user.getId()), null, corr);
        log.info("Password reset with a code. user={}", user.getId());
    }

        /**
     * Changes the password of the authenticated user (RF-MS01-08, flow B), every attempt, successful or failed, is
     * audited
     *
     * @param requester identity of whoever changes their password
     * @param request current password, new password and its confirmation
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws InvalidRequestException if the data breaks form F-06, the current password is incorrect or the new one is
     *         equal to the current one
     * @throws UserNotFoundException if the user no longer exists
     */
    @Override
    @Transactional
    public void changePassword(RequesterIdentity requester, ChangePasswordRequest request,
            String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);
        String resource = AuditRecorder.userResource(requester.userId());

        if (!requester.hasPermission(Permission.MS01_PASSWORD_CHANGE)) {
            auditRecorder.recordAs(AuditAction.CAMBIO_CONTRASENA, AuditResult.FALLO, requester, resource,
                    REASON_NOT_ALLOWED, corr);
            log.warn("Password change rejected: the requester {} does not have the required permission",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }

        List<ErrorResponse.ErrorDetail> errors = requestValidator.collectErrors(request);
        if (!errors.isEmpty()) {
            auditRecorder.recordAs(AuditAction.CAMBIO_CONTRASENA, AuditResult.FALLO, requester, resource,
                    REASON_VALIDATION, corr);
            log.warn("Password change rejected because of invalid data. user={}", requester.userId());
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, errors);
        }

        UserEntity user = userRepository.findById(requester.userId())
                .orElseThrow(() -> new UserNotFoundException(requester.userId()));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            auditRecorder.recordAs(AuditAction.CAMBIO_CONTRASENA, AuditResult.FALLO, requester, resource,
                    REASON_WRONG_CURRENT, corr);
            log.warn("Password change rejected: the current password is incorrect. user={}",
                    user.getId());
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, List.of(
                    new ErrorResponse.ErrorDetail("contrasena_actual",
                            ChangePasswordRequest.CURRENT_PASSWORD_MESSAGE)));
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            auditRecorder.recordAs(AuditAction.CAMBIO_CONTRASENA, AuditResult.FALLO, requester, resource,
                    REASON_SAME_AS_CURRENT, corr);
            log.warn("Password change rejected: the new password is equal to the current one. user={}",
                    user.getId());
            throw sameAsCurrent();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);

        auditRecorder.recordAs(AuditAction.CAMBIO_CONTRASENA, AuditResult.EXITO, requester, resource, null,
                corr);
        log.info("Password changed. user={}", user.getId());
    }

        /**
     * Generates a new code for the account, invalidates the previous ones and schedules the sending of the email for
     * after the transaction is committed
     *
     * @param user active account that requested the code
     * @param now instant of the request
     * @param corr correlation id of the request
     */
    private void issueCode(UserEntity user, Instant now, String corr) {
        String code = generateCode();

        codeRepository.invalidateUnused(user.getId(), now);

        PasswordResetCodeEntity entity = new PasswordResetCodeEntity();
        entity.setUserId(user.getId());
        entity.setCodeHash(passwordEncoder.encode(code));
        entity.setAttempts(0);
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(policy.codeTtl()));
        codeRepository.saveAndFlush(entity);

        String email = user.getEmail();
        mailer.sendAfterCommit("recovery code of user " + user.getId(),
                () -> emailService.sendRecoveryCode(email, code, policy.codeTtlMinutes()), corr);
        log.info("Recovery code generated. user={}", user.getId());
    }

        /**
     * Generates a six-digit code with a cryptographically secure generator
     *
     * @return the code with leading zeros if needed
     */
    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_BOUND));
    }

        /**
     * Tells whether the minimum wait since the last code generated for the user has not yet passed
     *
     * @param userId id of the user that requests the code
     * @param now instant of the request
     * @return true if the wait is active and another code must not be generated
     */
    private boolean isWithinCooldown(UUID userId, Instant now) {
        Duration cooldown = policy.resendCooldown();
        if (cooldown.isZero()) {
            return false;
        }
        Optional<PasswordResetCodeEntity> last = codeRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (last.isEmpty()) {
            return false;
        }
        Instant allowedAt = last.get().getCreatedAt().plus(cooldown);
        return allowedAt.isAfter(now);
    }

        /**
     * Counts a failed attempt of the code and invalidates it when the maximum number of attempts is reached
     *
     * @param code valid code against which the verification failed
     * @param now instant of the attempt
     */
    private void registerFailedAttempt(PasswordResetCodeEntity code, Instant now) {
        int attempts = code.getAttempts() + 1;
        code.setAttempts(attempts);
        if (attempts >= policy.maxAttempts()) {
            code.setUsedAt(now);
            log.warn("Recovery code invalidated for exceeding the allowed attempts. user={}",
                    code.getUserId());
        }
        codeRepository.saveAndFlush(code);
    }

        /**
     * Computes a padding hash to equalize the time of the branches that neither generate nor verify a code
     */
    private void spendHashingTime() {
        passwordEncoder.matches(dummyPassword, dummyHash);
    }

        /**
     * Audits and logs the rejection of the code, returns the exception so that the caller throws it
     *
     * @param corr correlation id of the request
     * @param userId id of the user, null if the email does not correspond to any account
     * @return the generic exception of invalid code
     */
    private InvalidResetCodeException rejectedCode(String corr, UUID userId) {
        auditRecorder.recordAnonymous(AuditAction.RECUPERACION, AuditResult.FALLO, userId,
                AuditRecorder.userResource(userId), REASON_INVALID_CODE, corr);
        log.warn("Recovery rejected. reason={} user={}", REASON_INVALID_CODE, userId);
        return new InvalidResetCodeException();
    }

        /**
     * Builds the rejection of a new password equal to the current one, with the message of form F-06
     *
     * @return the exception with the error of the new password field
     */
    private InvalidRequestException sameAsCurrent() {
        return new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, List.of(
                new ErrorResponse.ErrorDetail("nueva_contrasena", ChangePasswordRequest.NEW_PASSWORD_MESSAGE)));
    }
}
