package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IssuedToken;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtService;

/**
 * RF-MS01-02 Autenticar usuario (login).
 *
 * Flujo base: buscar por correo -> verificar contrasena contra el hash (BCrypt) -> verificar estado
 * ACTIVO -> emitir JWT -> auditar LOGIN/EXITO -> responder token, expiracion y datos basicos.
 *
 * RN-MS01-05: Ante credenciales inválidas no se revela si falló el correo o la contraseña.
 * RN-MS01-06: Una cuenta INACTIVA nunca inicia sesión, aunque las credenciales sean correctas.
 * 
 * RNF-MS01-10: Se audita logeo exitoso, credenciales invalidas, y cuenta inactiva.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    static final String REASON_BAD_CREDENTIALS = "CREDENCIALES_INCORRECTAS";
    static final String REASON_INACTIVE = "CUENTA_INACTIVA";
    static final String REASON_INTERNAL = "ERROR_INTERNO";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[\\x21-\\x7E]{1,100}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    /**
     * Por seguridad se usa un hash de relleno, contra el que se compara la contraseña ingresada
     * cuando un correo no existe, esto simula el tiempo que tarda el servidor y backend en
     * buscar un correo, evitando revelar por tiempo, si el correo ingresado si esta registrado o no.
     */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, AuditEventPublisher auditPublisher, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    
    /**
     * Autentica con correo y contrasena (RF-MS01-02) y emite el JWT (RF-MS01-03)
     *
     * @param request contiene las credenciales; correo y contraseña
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoria
     * @return LoginResponse que contiene token, tipo, expiracion y datos basicos del usuario
     * @throws edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException
     * @throws edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException
     */
    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String correlationId) {
        String corr = resolveCorrelationId(correlationId);
        String email = normalizeEmail(request.getCorreo());
        String rawPassword = request.getContrasena();

        Optional<UserEntity> candidate = userRepository.findByEmail(email);
        if (candidate.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            throw rejected(corr, null, email, REASON_BAD_CREDENTIALS, new InvalidCredentialsException());
        }

        UserEntity user = candidate.get();
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw rejected(corr, user.getId(), email, REASON_BAD_CREDENTIALS, new InvalidCredentialsException());
        }
        if (user.getState() != UserState.ACTIVO) {
            throw rejected(corr, user.getId(), email, REASON_INACTIVE, new InactiveAccountException());
        }

        List<UserRole> roles = sortedRoles(user.getRoles());
        IssuedToken token;
        try {
            token = jwtService.issueToken(user.getId(), user.getEmail(), roles);
        } catch (RuntimeException e) {
            publish(AuditResult.FALLO, user.getId(), email, REASON_INTERNAL, corr);
            log.error("[{}] Login fallido: no fue posible emitir el JWT del usuario {}", corr, user.getId(), e);
            throw e;
        }

        publish(AuditResult.EXITO, user.getId(), email, null, corr);
        log.info("[{}] Login exitoso. usuario={}", corr, user.getId());

        return new LoginResponse(
                token.token(),
                LoginResponse.TOKEN_TYPE,
                DateTimeFormatter.ISO_INSTANT.format(token.expiresAt()),
                new LoginResponse.UsuarioResumen(user.getId(), user.getEmail(), roles));
    }

    /** Audita y registra el intento fallido; devuelve la excepcion para que el llamador la lance.
     * 
     * @param corr Es el correlationID
     * @param userId Id del usuario
     * @param emal Correo del usuario
     * @param reason Razon de la excepcion / fallo
     * @param exception Excepcion recibida 
     * @return La excepcion recibida
    */
    private <E extends RuntimeException> E rejected(String corr, UUID userId, String email, String reason, E exception) {
        publish(AuditResult.FALLO, userId, email, reason, corr);
        log.warn("[{}] Login rechazado. motivo={} usuario={}", corr, reason, userId);
        return exception;
    }

    /** La auditoria nunca debe alterar la respuesta al cliente (RNF-MS01-10).
     * 
     * @param result Indica si lo que se audita es una operacion exitosa o fallida
     * @param userId Id del usuario
     * @param email Correo del usuario
     * @param detail Detalle a incluir en la auditoria
     * @param corr CorrelationId
    */
    private void publish(AuditResult result, UUID userId, String email, String detail, String corr) {
        try {
            auditPublisher.publish(new AuditEvent(AuditAction.LOGIN, result, userId, email, detail, corr, clock.instant()));
        } catch (RuntimeException e) {
            log.error("[{}] Falló la emisión del evento de auditoría LOGIN/{}: {}", corr, result, e.toString());
        }
    }

    /**
     * Ordena la lista de roles en el orden del abecedario
     * Si no existe la lista entonces retorna una vacia
     * 
     * @param roles Lista de roles del usuario
     * @return La lista de roles del usuario ordenada
     */
    private static List<UserRole> sortedRoles(List<UserRole> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream().sorted(Comparator.comparing(UserRole::name)).toList();
    }

    /**
     * Normaliza el correo de un usuario, eliminando espacios a los lados y pasandolo todo a minusculas
     * 
     * @param email correo del usuario
     * @return Coreo del usuario normalizado segun lo especificado en requerimientos y formatos
     */
    private static String normalizeEmail(String email) {
        String normalizedEmail;
        if (email == null) {
            normalizedEmail = null;
        } else {
            normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        }
        return normalizedEmail;
    }

    /**
     * Normaliza el correlationID, y verifica que cumpla con el patron establecido
     * 
     * Si no existe un correlationId entonces lo genera aleatoriamente
     * 
     * @param correlationId
     * @return Un correlationId normalizado
     */
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
