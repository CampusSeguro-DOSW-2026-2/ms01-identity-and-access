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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IssuedToken;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;

/**
 * RF-MS01-02 Autenticar usuario (login)
 *
 * Flujo base: buscar por correo -> verificar contrasena contra el hash (BCrypt) -> verificar estado
 * ACTIVO -> emitir JWT -> auditar LOGIN/EXITO -> responder token, expiracion y datos basicos
 *
 * RN-MS01-05: Ante credenciales inválidas no se revela si falló el correo o la contraseña
 * RN-MS01-06: Una cuenta INACTIVA nunca inicia sesión, aunque las credenciales sean correctas
 *
 * RNF-MS01-10: Se audita logeo exitoso, credenciales invalidas, y cuenta inactiva
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
     * Por seguridad se usa un hash de relleno, contra el que se compara la contraseña ingresada
     * cuando un correo no existe, esto simula el tiempo que tarda el servidor y backend en
     * buscar un correo, evitando revelar por tiempo, si el correo ingresado si esta registrado o no
     */
    private final String dummyHash;

    /**
     * Crea el servicio con sus dependencias
     *
     * @param userRepository repositorio de acceso a los usuarios y sus roles
     * @param passwordEncoder codificador con el que se verifican las contraseñas
     * @param jwtService emisor del JWT
     * @param auditRecorder registrador de los eventos de auditoría de MS08
     * @param requestValidator validador de los cuerpos de las peticiones
     */
    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, AuditRecorder auditRecorder, RequestValidator requestValidator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditRecorder = auditRecorder;
        this.requestValidator = requestValidator;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Autentica con correo y contraseña (RF-MS01-02) y emite el JWT (RF-MS01-03)
     *
     * @param request contiene las credenciales; correo y contraseña
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return LoginResponse que contiene token, tipo, expiración y datos básicos del usuario
     * @throws edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException si el cuerpo
     *         no cumple el formulario F-02
     * @throws InvalidCredentialsException si el correo no existe o la contraseña no coincide
     * @throws InactiveAccountException si las credenciales son correctas pero la cuenta está inactiva
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

        List<UserRole> roles = UserRole.sortedByName(user.getRoles());
        IssuedToken token;
        try {
            token = jwtService.issueToken(user.getId(), user.getEmail(), roles);
        } catch (RuntimeException e) {
            auditRecorder.recordAnonymous(AuditAction.LOGIN, AuditResult.FALLO, user.getId(),
                    AuditRecorder.userResource(user.getId()), REASON_INTERNAL, corr);
            log.error("Login fallido: no fue posible emitir el JWT del usuario {}", user.getId(), e);
            throw e;
        }

        auditRecorder.recordAnonymous(AuditAction.LOGIN, AuditResult.EXITO, user.getId(),
                AuditRecorder.userResource(user.getId()), null, corr);
        log.info("Login exitoso. usuario={}", user.getId());

        return new LoginResponse(
                token.token(),
                LoginResponse.TOKEN_TYPE,
                DateTimeFormatter.ISO_INSTANT.format(token.expiresAt()),
                new LoginResponse.UsuarioResumen(user.getId(), user.getEmail(), roles));
    }

    /**
     * Audita y registra el intento fallido, devuelve la excepción para que el llamador la lance
     *
     * @param corr correlation id de la petición
     * @param userId id del usuario, null si el correo no corresponde a ninguna cuenta
     * @param reason motivo del fallo que se deja en el detalle del evento
     * @param exception excepción que se lanzará al cliente
     * @param <E> tipo de la excepción
     * @return la excepción recibida
     */
    private <E extends RuntimeException> E rejected(String corr, UUID userId, String reason, E exception) {
        auditRecorder.recordAnonymous(AuditAction.LOGIN, AuditResult.FALLO, userId,
                AuditRecorder.userResource(userId), reason, corr);
        log.warn("Login rechazado. motivo={} usuario={}", reason, userId);
        return exception;
    }
}
