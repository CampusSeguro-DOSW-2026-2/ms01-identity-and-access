package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.config.PasswordRecoveryConfig;
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
 * RF-MS01-08 Recuperación y cambio de contraseña
 *
 * Flujo base A (restablecer): solicitar el código -> generar un código de 6 dígitos, guardar solo su hash y
 * enviarlo por correo -> validar correo, código y nueva contraseña -> actualizar la contraseña, invalidar el
 * código y auditar RECUPERACION
 *
 * Flujo base B (cambiar): validar la contraseña actual, la política y que la nueva sea distinta de la
 * actual -> actualizar la contraseña y auditar CAMBIO_CONTRASENA
 *
 * RN-MS01-20: La respuesta de la solicitud de código no revela si el correo existe, ni por el contenido ni
 * por el tiempo de respuesta: el correo se envía fuera del hilo de la petición y las ramas que no generan un
 * código gastan el mismo tiempo de hash que la que sí lo genera
 * RN-MS01-21: El código es de un solo uso, de vigencia corta y configurable, y solo hay un código vigente por
 * usuario, además se invalida tras un número máximo de intentos fallidos y entre dos solicitudes debe pasar
 * un tiempo mínimo
 * RN-MS01-22: La nueva contraseña cumple la política y se guarda como hash BCrypt
 *
 * RNF-MS01-10: Se audita el restablecimiento y el cambio, exitosos y fallidos, la auditoría nunca incluye
 * contraseñas, hashes, códigos ni correos y un fallo al auditar no cambia la respuesta
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
    private final Executor mailExecutor;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Por seguridad se usa una contraseña y un hash de relleno contra los que se calcula un hash cuando la
     * solicitud no genera un código o el código recibido no corresponde a ninguna cuenta, esto iguala el
     * tiempo de respuesta de todas las ramas y evita revelar por tiempo si un correo está registrado
     */
    private final String dummyPassword = UUID.randomUUID().toString();
    private final String dummyHash;

    /**
     * Crea el servicio con sus dependencias
     *
     * @param userRepository repositorio de acceso a los usuarios
     * @param codeRepository repositorio de acceso a los códigos de recuperación
     * @param passwordEncoder codificador con el que se guardan y verifican las contraseñas y los códigos
     * @param emailService servicio de envío del código por correo
     * @param auditRecorder registrador de los eventos de auditoría de MS08
     * @param requestValidator validador de los cuerpos de las peticiones
     * @param clock reloj UTC con el que se fechan y vencen los códigos
     * @param policy vigencia, intentos máximos y espera entre solicitudes de los códigos
     * @param mailExecutor ejecutor con el que se envían los correos fuera del hilo de la petición
     */
    public PasswordServiceImpl(UserRepository userRepository, PasswordResetCodeRepository codeRepository,
            PasswordEncoder passwordEncoder, EmailService emailService, AuditRecorder auditRecorder,
            RequestValidator requestValidator, Clock clock, RecoveryPolicy policy,
            @Qualifier(PasswordRecoveryConfig.MAIL_EXECUTOR) Executor mailExecutor) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditRecorder = auditRecorder;
        this.requestValidator = requestValidator;
        this.clock = clock;
        this.policy = policy;
        this.mailExecutor = mailExecutor;
        this.dummyHash = passwordEncoder.encode(dummyPassword);
    }

    /**
     * Solicita el código de recuperación de contraseña (RF-MS01-08, flujo A), el código se genera y se envía
     * solo si el correo corresponde a una cuenta activa y pasó la espera desde el código anterior, pero la
     * respuesta es la misma en todos los casos (RN-MS01-20)
     *
     * @param request correo de la cuenta que se quiere recuperar
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @throws InvalidRequestException si el correo incumple el formulario F-04
     */
    @Override
    @Transactional
    public void requestRecoveryCode(ForgotPasswordRequest request, String correlationId) {
        requestValidator.validate(request);
        String corr = CorrelationIds.resolve(correlationId);

        Optional<UserEntity> candidate = userRepository.findByEmail(request.getEmail());
        if (candidate.isEmpty()) {
            spendHashingTime();
            log.info("Solicitud de código de recuperación sin cuenta asociada");
            return;
        }

        UserEntity user = candidate.get();
        if (user.getState() != UserState.ACTIVO) {
            spendHashingTime();
            log.info("Solicitud de código de recuperación de una cuenta inactiva. usuario={}", user.getId());
            return;
        }

        Instant now = clock.instant();
        if (isWithinCooldown(user.getId(), now)) {
            spendHashingTime();
            log.info("Solicitud de código de recuperación dentro de la espera mínima. usuario={}", user.getId());
            return;
        }

        issueCode(user, now, corr);
    }

    /**
     * Restablece la contraseña con el código recibido por correo (RF-MS01-08, flujo A), el código es de un
     * solo uso y cada intento, exitoso o fallido, queda auditado, los intentos fallidos con un código
     * vigente se cuentan aunque la petición se rechace
     *
     * @param request correo, código y nueva contraseña con su confirmación
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @throws InvalidRequestException si los datos incumplen el formulario F-05 o la nueva contraseña es igual
     *         a la actual
     * @throws InvalidResetCodeException si el código es incorrecto, venció o ya se usó
     */
    @Override
    @Transactional(noRollbackFor = InvalidResetCodeException.class)
    public void resetPassword(ResetPasswordRequest request, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        List<ErrorResponse.ErrorDetail> errors = requestValidator.collectErrors(request);
        if (!errors.isEmpty()) {
            auditRecorder.recordAnonymous(AuditAction.RECUPERACION, AuditResult.FALLO, null, null,
                    REASON_VALIDATION, corr);
            log.warn("Recuperación rechazada por datos inválidos");
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
            log.warn("Recuperación rechazada: la nueva contraseña es igual a la actual. usuario={}",
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
        log.info("Contraseña restablecida con código. usuario={}", user.getId());
    }

    /**
     * Cambia la contraseña del usuario autenticado (RF-MS01-08, flujo B), cada intento, exitoso o fallido,
     * queda auditado
     *
     * @param requester identidad de quien cambia su contraseña
     * @param request contraseña actual, nueva contraseña y su confirmación
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws InvalidRequestException si los datos incumplen el formulario F-06, la contraseña actual es
     *         incorrecta o la nueva es igual a la actual
     * @throws UserNotFoundException si el usuario ya no existe
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
            log.warn("Cambio de contraseña rechazado: el solicitante {} no tiene el permiso requerido",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }

        List<ErrorResponse.ErrorDetail> errors = requestValidator.collectErrors(request);
        if (!errors.isEmpty()) {
            auditRecorder.recordAs(AuditAction.CAMBIO_CONTRASENA, AuditResult.FALLO, requester, resource,
                    REASON_VALIDATION, corr);
            log.warn("Cambio de contraseña rechazado por datos inválidos. usuario={}", requester.userId());
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, errors);
        }

        UserEntity user = userRepository.findById(requester.userId())
                .orElseThrow(() -> new UserNotFoundException(requester.userId()));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            auditRecorder.recordAs(AuditAction.CAMBIO_CONTRASENA, AuditResult.FALLO, requester, resource,
                    REASON_WRONG_CURRENT, corr);
            log.warn("Cambio de contraseña rechazado: la contraseña actual es incorrecta. usuario={}",
                    user.getId());
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, List.of(
                    new ErrorResponse.ErrorDetail("contrasena_actual",
                            ChangePasswordRequest.CURRENT_PASSWORD_MESSAGE)));
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            auditRecorder.recordAs(AuditAction.CAMBIO_CONTRASENA, AuditResult.FALLO, requester, resource,
                    REASON_SAME_AS_CURRENT, corr);
            log.warn("Cambio de contraseña rechazado: la nueva contraseña es igual a la actual. usuario={}",
                    user.getId());
            throw sameAsCurrent();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);

        auditRecorder.recordAs(AuditAction.CAMBIO_CONTRASENA, AuditResult.EXITO, requester, resource, null,
                corr);
        log.info("Contraseña cambiada. usuario={}", user.getId());
    }

    /**
     * Genera un código nuevo para la cuenta, invalida los anteriores y programa el envío del correo para
     * después de confirmar la transacción
     *
     * @param user cuenta activa que solicitó el código
     * @param now instante de la solicitud
     * @param corr correlation id de la petición
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

        scheduleEmail(user.getId(), user.getEmail(), code, corr);
        log.info("Código de recuperación generado. usuario={}", user.getId());
    }

    /**
     * Genera un código de seis dígitos con un generador criptográficamente seguro
     *
     * @return el código con ceros a la izquierda si hace falta
     */
    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_BOUND));
    }

    /**
     * Indica si todavía no pasó la espera mínima desde el último código generado al usuario
     *
     * @param userId id del usuario que solicita el código
     * @param now instante de la solicitud
     * @return true si la espera está activa y no se debe generar otro código
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
     * Cuenta un intento fallido del código y lo invalida cuando se alcanza el máximo de intentos
     *
     * @param code código vigente contra el que falló la verificación
     * @param now instante del intento
     */
    private void registerFailedAttempt(PasswordResetCodeEntity code, Instant now) {
        int attempts = code.getAttempts() + 1;
        code.setAttempts(attempts);
        if (attempts >= policy.maxAttempts()) {
            code.setUsedAt(now);
            log.warn("Código de recuperación invalidado por exceder los intentos permitidos. usuario={}",
                    code.getUserId());
        }
        codeRepository.saveAndFlush(code);
    }

    /**
     * Programa el envío del correo para después de confirmar la transacción, así nunca se envía un código
     * que no quedó guardado, si no hay una transacción activa se envía de inmediato
     *
     * @param userId id del usuario destinatario
     * @param email correo del destinatario
     * @param code código en claro que se envía
     * @param corr correlation id de la petición
     */
    private void scheduleEmail(UUID userId, String email, String code, String corr) {
        Runnable delivery = new Runnable() {
            @Override
            public void run() {
                deliver(userId, email, code, corr);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(delivery, userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(delivery, userId);
            }
        });
    }

    /**
     * Entrega el envío al ejecutor de correos, si la cola está llena el envío se descarta y se registra
     *
     * @param delivery tarea que envía el correo
     * @param userId id del usuario destinatario
     */
    private void dispatch(Runnable delivery, UUID userId) {
        try {
            mailExecutor.execute(delivery);
        } catch (RejectedExecutionException e) {
            log.error("Envío del código de recuperación descartado: la cola de correo está llena. usuario={}",
                    userId);
        }
    }

    /**
     * Envía el correo con el código, un fallo del servidor de correo se registra sin el código ni el
     * correo y nunca llega al cliente
     *
     * @param userId id del usuario destinatario
     * @param email correo del destinatario
     * @param code código en claro que se envía
     * @param corr correlation id de la petición, se coloca en el contexto de logs del hilo de envío
     */
    private void deliver(UUID userId, String email, String code, String corr) {
        MDC.put(CorrelationIds.MDC_KEY, corr);
        try {
            emailService.sendRecoveryCode(email, code, policy.codeTtlMinutes());
            log.info("Código de recuperación enviado. usuario={}", userId);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el código de recuperación. usuario={} causa={}", userId,
                    rootCauseName(e));
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

    /**
     * Obtiene el nombre de la clase de la causa raíz de un error, sin su mensaje porque puede contener el
     * correo del destinatario
     *
     * @param error error del que se obtiene la causa raíz
     * @return el nombre simple de la clase de la causa raíz
     */
    private String rootCauseName(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    /**
     * Calcula un hash de relleno para igualar el tiempo de las ramas que no generan ni verifican un código
     */
    private void spendHashingTime() {
        passwordEncoder.matches(dummyPassword, dummyHash);
    }

    /**
     * Audita y registra el rechazo del código, devuelve la excepción para que el llamador la lance
     *
     * @param corr correlation id de la petición
     * @param userId id del usuario, null si el correo no corresponde a ninguna cuenta
     * @return la excepción genérica de código inválido
     */
    private InvalidResetCodeException rejectedCode(String corr, UUID userId) {
        auditRecorder.recordAnonymous(AuditAction.RECUPERACION, AuditResult.FALLO, userId,
                AuditRecorder.userResource(userId), REASON_INVALID_CODE, corr);
        log.warn("Recuperación rechazada. motivo={} usuario={}", REASON_INVALID_CODE, userId);
        return new InvalidResetCodeException();
    }

    /**
     * Construye el rechazo de una nueva contraseña igual a la actual, con el mensaje del formulario F-06
     *
     * @return la excepción con el error del campo de la nueva contraseña
     */
    private InvalidRequestException sameAsCurrent() {
        return new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, List.of(
                new ErrorResponse.ErrorDetail("nueva_contrasena", ChangePasswordRequest.NEW_PASSWORD_MESSAGE)));
    }
}
