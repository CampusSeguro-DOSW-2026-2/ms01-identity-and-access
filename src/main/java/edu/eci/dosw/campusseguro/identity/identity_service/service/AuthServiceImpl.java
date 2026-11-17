package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
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
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IssuedToken;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;

/**
 * RF-MS01-02 Authenticate user (login)
 *
 * Base flow: look up by email -> verify password against the hash (BCrypt) -> verify ACTIVO status -> read the
 * permissions of its roles (role_permissions and permissions) -> issue JWT -> audit LOGIN/EXITO -> respond token,
 * expiration and basic data
 *
 * RN-MS01-05: With invalid credentials it is not revealed whether the email or the password failed RN-MS01-06: An
 * INACTIVA account never logs in, even if the credentials are correct
 *
 * RNF-MS01-10: Successful login, invalid credentials and inactive account are audited
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    static final String REASON_BAD_CREDENTIALS = "CREDENCIALES_INCORRECTAS";
    static final String REASON_INACTIVE = "CUENTA_INACTIVA";
    static final String REASON_INTERNAL = "ERROR_INTERNO";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditRecorder auditRecorder;
    private final RequestValidator requestValidator;

        /**
     * For security a padding hash is used, against which the entered password is compared when an email does not exist,
     * this simulates the time the server and backend take to look up an email, avoiding revealing through timing
     * whether the entered email is registered or not
     */
    private final String dummyHash;

        /**
     * Creates the service with its dependencies
     *
     * @param userRepository repository to access the users, their roles and the permissions of each role
     * @param passwordEncoder encoder with which the passwords are verified
     * @param jwtService JWT issuer
     * @param auditRecorder recorder of the audit events of MS08
     * @param requestValidator validator of the request bodies
     */
    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
            AuditRecorder auditRecorder, RequestValidator requestValidator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditRecorder = auditRecorder;
        this.requestValidator = requestValidator;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

        /**
     * Authenticates with email and password (RF-MS01-02) and issues the JWT (RF-MS01-03)
     *
     * @param request contains the credentials; email and password
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return LoginResponse containing token, type, expiration and basic data of the user
     * @throws edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException if the body does
     *         not meet form F-02
     * @throws InvalidCredentialsException if the email does not exist or the password does not match
     * @throws InactiveAccountException if the credentials are correct but the account is inactive
     */
    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String correlationId) {
        requestValidator.validate(request);

        String corr = CorrelationIds.resolve(correlationId);
        String email = request.getCorreo();
        String rawPassword = request.getContrasena();

        Optional<UserEntity> candidate = userRepository.findByEmail(email);
        if (candidate.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            throw rejected(corr, null, REASON_BAD_CREDENTIALS, new InvalidCredentialsException());
        }

        UserEntity user = candidate.get();
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw rejected(corr, user.getId(), REASON_BAD_CREDENTIALS, new InvalidCredentialsException());
        }
        if (user.getState() != UserState.ACTIVO) {
            throw rejected(corr, user.getId(), REASON_INACTIVE, new InactiveAccountException());
        }

        List<String> roles = user.getRoleNames();
        IssuedToken token;
        try {
            List<String> permissions = PermissionCodes.effective(user.getRoles());
            token = jwtService.issueToken(user.getId(), user.getEmail(), roles, permissions);
        } catch (RuntimeException e) {
            auditRecorder.recordAnonymous(AuditAction.LOGIN, AuditResult.FALLO, user.getId(),
                    AuditRecorder.userResource(user.getId()), REASON_INTERNAL, corr);
            log.error("Login failed: the JWT of user {} could not be issued", user.getId(), e);
            throw e;
        }

        auditRecorder.recordAnonymous(AuditAction.LOGIN, AuditResult.EXITO, user.getId(),
                AuditRecorder.userResource(user.getId()), null, corr);
        log.info("Login exitoso. usuario={}", user.getId());

        return new LoginResponse(
                token.token(),
                LoginResponse.TOKEN_TYPE,
                DateTimeFormatter.ISO_INSTANT.format(token.expiresAt()),
                new LoginResponse.UserSummary(user.getId(), user.getEmail(), roles));
    }

        /**
     * Audits and records the failed attempt, returns the exception so that the caller throws it
     *
     * @param corr correlation id of the request
     * @param userId id of the user, null if the email does not correspond to any account
     * @param reason reason of the failure that is left in the detail of the event
     * @param exception exception that will be thrown to the client
     * @param <E> type of the exception
     * @return the received exception
     */
    private <E extends RuntimeException> E rejected(String corr, UUID userId, String reason, E exception) {
        auditRecorder.recordAnonymous(AuditAction.LOGIN, AuditResult.FALLO, userId,
                AuditRecorder.userResource(userId), reason, corr);
        log.warn("Login rechazado. motivo={} usuario={}", reason, userId);
        return exception;
    }
}
