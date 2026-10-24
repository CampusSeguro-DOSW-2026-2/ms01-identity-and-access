package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidResetCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.PasswordMismatchException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.SameAsCurrentPasswordException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.WrongCurrentPasswordException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PasswordResetCodeRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import jakarta.transaction.Transactional;

/**
 * RF-MS01-08 Recuperar contrasena (flujo A) y cambiarla estando autenticado (flujo B).
 *
 * RN-MS01-20: la solicitud de codigo no revela si el correo existe.
 * RN-MS01-21: el codigo es de un solo uso y de vigencia corta y configurable.
 * RN-MS01-22: la nueva contrasena se almacena solo como hash BCrypt.
 * RNF-MS01-10: el restablecimiento (exitoso o fallido) se audita hacia MS08.
 */
@Service
public class PasswordServiceImpl implements PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[\\x21-\\x7E]{1,100}$");

    static final String REASON_INVALID_CODE = "CODIGO_INVALIDO";
    static final String REASON_MISMATCH = "CONFIRMACION_DISTINTA";
    static final String REASON_WRONG_CURRENT = "CONTRASENA_ACTUAL_INCORRECTA";
    static final String REASON_SAME_AS_CURRENT = "NUEVA_IGUAL_A_ACTUAL";

    private static final String GENERIC_MESSAGE =
            "Si el correo está registrado, enviamos un código de recuperación.";
    private static final String SUCCESS_MESSAGE =
            "Contraseña actualizada correctamente. Inicia sesión de nuevo.";
    private static final String CHANGE_SUCCESS_MESSAGE = "Contraseña actualizada correctamente.";

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;
    private final int validMinutes;
    private final int maxAttempts;
    private final int cooldownSeconds;

    public PasswordServiceImpl(UserRepository userRepository,
            PasswordResetCodeRepository codeRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuditEventPublisher auditPublisher,
            Clock clock,
            @Value("${campusseguro.recuperacion.vigencia-minutos}") int validMinutes,
            @Value("${campusseguro.recuperacion.max-intentos}") int maxAttempts,
            @Value("${campusseguro.recuperacion.espera-segundos}") int cooldownSeconds) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
        this.validMinutes = validMinutes;
        this.maxAttempts = maxAttempts;
        this.cooldownSeconds = cooldownSeconds;
    }

    /**
     * Genera y envia un codigo de recuperacion si la cuenta existe, esta activa y paso el tiempo de
     * espera. La respuesta es siempre la misma (RN-MS01-20).
     */
    @Override
    @Transactional
    public MessageResponse requestResetCode(ForgotPasswordRequest request, String correlationId) {
        String corr = resolveCorrelationId(correlationId);
        String email = request.correo().trim().toLowerCase();

        userRepository.findByEmail(email)
                .filter(user -> user.getState() == UserState.ACTIVO)
                .filter(this::cooldownElapsed)
                .ifPresent(user -> issueCode(user, corr));

        return new MessageResponse(GENERIC_MESSAGE);
    }

    /**
     * Valida el codigo y cambia la contrasena. Cualquier fallo del codigo responde igual (400) sin
     * revelar la causa, y se audita como RECUPERACION/FALLO.
     */
    @Override
    @Transactional(dontRollbackOn = InvalidResetCodeException.class)
    public MessageResponse resetPassword(ResetPasswordRequest request, String correlationId) {
        String corr = resolveCorrelationId(correlationId);
        String email = request.correo().trim().toLowerCase();

        if (!request.nueva_contrasena().equals(request.confirmar_contrasena())) {
            publish(AuditResult.FALLO, null, email, REASON_MISMATCH, corr);
            throw new PasswordMismatchException();
        }

        UserEntity user = userRepository.findByEmail(email)
                .filter(u -> u.getState() == UserState.ACTIVO)
                .orElseThrow(() -> rejected(corr, null, email));

        PasswordResetCodeEntity code = codeRepository
                .findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)
                .orElseThrow(() -> rejected(corr, user.getId(), email));

        if (clock.instant().isAfter(code.getExpiresAt()) || code.getAttempts() >= maxAttempts) {
            throw rejected(corr, user.getId(), email);
        }

        if (!passwordEncoder.matches(request.codigo(), code.getCodeHash())) {
            code.setAttempts(code.getAttempts() + 1);
            codeRepository.save(code);
            throw rejected(corr, user.getId(), email);
        }

        user.setPassword(passwordEncoder.encode(request.nueva_contrasena()));
        userRepository.save(user);

        code.setUsedAt(clock.instant());
        codeRepository.save(code);

        publish(AuditResult.EXITO, user.getId(), email, null, corr);
        log.info("[{}] Recuperacion exitosa. usuario={}", corr, user.getId());
        return new MessageResponse(SUCCESS_MESSAGE);
    }

    /**
     * Flujo B (F-06): cambia la contrasena del usuario autenticado. Valida la confirmacion, la
     * contrasena actual y que la nueva sea distinta de la actual. Todo intento se audita como
     * CAMBIO_CONTRASENA (EXITO o FALLO).
     *
     * @param userId        id del usuario, tomado de la cabecera X-User-Id
     * @param request       contrasena actual, nueva y confirmacion
     * @param correlationId valor de X-Correlation-ID; si falta o es invalido se genera uno
     */
    @Override
    @Transactional
    public MessageResponse changePassword(UUID userId, ChangePasswordRequest request, String correlationId) {
        String corr = resolveCorrelationId(correlationId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        String email = user.getEmail();

        if (!request.nueva_contrasena().equals(request.confirmar_contrasena())) {
            publishChange(AuditResult.FALLO, userId, email, REASON_MISMATCH, corr);
            throw new PasswordMismatchException();
        }

        if (!passwordEncoder.matches(request.contrasena_actual(), user.getPassword())) {
            publishChange(AuditResult.FALLO, userId, email, REASON_WRONG_CURRENT, corr);
            log.warn("[{}] Cambio de contrasena rechazado. motivo={} usuario={}", corr, REASON_WRONG_CURRENT, userId);
            throw new WrongCurrentPasswordException();
        }

        if (passwordEncoder.matches(request.nueva_contrasena(), user.getPassword())) {
            publishChange(AuditResult.FALLO, userId, email, REASON_SAME_AS_CURRENT, corr);
            throw new SameAsCurrentPasswordException();
        }

        user.setPassword(passwordEncoder.encode(request.nueva_contrasena()));
        userRepository.save(user);

        publishChange(AuditResult.EXITO, userId, email, null, corr);
        log.info("[{}] Cambio de contrasena exitoso. usuario={}", corr, userId);
        return new MessageResponse(CHANGE_SUCCESS_MESSAGE);
    }

    private void issueCode(UserEntity user, String corr) {
        Instant now = clock.instant();

        List<PasswordResetCodeEntity> pending = codeRepository.findByUserAndUsedAtIsNull(user);
        pending.forEach(old -> old.setUsedAt(now));
        codeRepository.saveAll(pending);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        PasswordResetCodeEntity entity = new PasswordResetCodeEntity();
        entity.setUser(user);
        entity.setCodeHash(passwordEncoder.encode(code));
        entity.setExpiresAt(now.plus(validMinutes, ChronoUnit.MINUTES));
        codeRepository.save(entity);

        try {
            emailService.sendResetCode(user.getEmail(), code, validMinutes);
        } catch (MailException ex) {
            log.error("[{}] No se pudo enviar el código de recuperación al usuario {}", corr, user.getId(), ex);
        }
    }

    private boolean cooldownElapsed(UserEntity user) {
        return codeRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .map(last -> clock.instant().isAfter(last.getCreatedAt().plusSeconds(cooldownSeconds)))
                .orElse(true);
    }

    /** Audita el fallo del codigo y devuelve la excepcion para que el llamador la lance. */
    private InvalidResetCodeException rejected(String corr, UUID userId, String email) {
        publish(AuditResult.FALLO, userId, email, REASON_INVALID_CODE, corr);
        log.warn("[{}] Recuperacion rechazada. motivo={} usuario={}", corr, REASON_INVALID_CODE, userId);
        return new InvalidResetCodeException();
    }

    /** La auditoria nunca debe alterar la respuesta al cliente (RNF-MS01-10). */
    private void publish(AuditResult result, UUID userId, String email, String detail, String corr) {
        try {
            auditPublisher.publish(new AuditEvent(
                    AuditAction.RECUPERACION, result, userId, email, detail, corr, clock.instant()));
        } catch (RuntimeException e) {
            log.error("[{}] Falló la emisión del evento de auditoría RECUPERACION/{}: {}", corr, result, e.toString());
        }
    }

    /** Igual que publish, pero para la accion CAMBIO_CONTRASENA (flujo B). */
    private void publishChange(AuditResult result, UUID userId, String email, String detail, String corr) {
        try {
            auditPublisher.publish(new AuditEvent(
                    AuditAction.CAMBIO_CONTRASENA, result, userId, email, detail, corr, clock.instant()));
        } catch (RuntimeException e) {
            log.error("[{}] Falló la emisión del evento de auditoría CAMBIO_CONTRASENA/{}: {}", corr, result, e.toString());
        }
    }

    private static String resolveCorrelationId(String correlationId) {
        if (correlationId != null) {
            String trimmed = correlationId.trim();
            if (SAFE_CORRELATION_ID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }
}