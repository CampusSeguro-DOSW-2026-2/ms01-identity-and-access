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
 * El orden de las verificaciones es parte del contrato: el estado solo se evalua despues de que la
 * contrasena es correcta, de modo que un atacante no puede averiguar si una cuenta existe o esta
 * inactiva sin conocer su contrasena (RN-MS01-05). Todos los intentos, exitosos o no, se auditan.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** Motivos de fallo que viajan a MS08. Son genericos: no distinguen correo inexistente de contrasena erronea. */
    static final String REASON_BAD_CREDENTIALS = "CREDENCIALES_INCORRECTAS";
    static final String REASON_INACTIVE = "CUENTA_INACTIVA";
    static final String REASON_INTERNAL = "ERROR_INTERNO";

    /** Un Correlation ID legitimo es ASCII imprimible sin espacios; se descarta cualquier otra cosa. */
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[\\x21-\\x7E]{1,100}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    /**
     * Hash de relleno: cuando el correo no existe se compara igualmente contra el para que la
     * respuesta tarde lo mismo que con un correo existente (evita revelar cuentas por tiempo).
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

    /** Audita y registra el intento fallido; devuelve la excepcion para que el llamador la lance. */
    private <E extends RuntimeException> E rejected(String corr, UUID userId, String email, String reason, E exception) {
        publish(AuditResult.FALLO, userId, email, reason, corr);
        log.warn("[{}] Login rechazado. motivo={} usuario={}", corr, reason, userId);
        return exception;
    }

    /** La auditoria nunca debe alterar la respuesta al cliente (RNF-MS01-10). */
    private void publish(AuditResult result, UUID userId, String email, String detail, String corr) {
        try {
            auditPublisher.publish(new AuditEvent(AuditAction.LOGIN, result, userId, email, detail, corr, clock.instant()));
        } catch (RuntimeException e) {
            log.error("[{}] Falló la emisión del evento de auditoría LOGIN/{}: {}", corr, result, e.toString());
        }
    }

    private static List<UserRole> sortedRoles(List<UserRole> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream().sorted(Comparator.comparing(UserRole::name)).toList();
    }

    private static String normalizeEmail(String email) {
        return (email == null) ? null : email.trim().toLowerCase(Locale.ROOT);
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
