package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PasswordResetCodeEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidResetCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.PasswordMismatchException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PasswordResetCodeRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import jakarta.transaction.Transactional;

@Service
public class PasswordServiceImpl implements PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String GENERIC_MESSAGE =
            "Si el correo está registrado, enviamos un código de recuperación.";
    private static final String SUCCESS_MESSAGE =
            "Contraseña actualizada correctamente. Inicia sesión de nuevo.";

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final int validMinutes;
    private final int maxAttempts;

    public PasswordServiceImpl(UserRepository userRepository,
            PasswordResetCodeRepository codeRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${campusseguro.recuperacion.vigencia-minutos}") int validMinutes,
            @Value("${campusseguro.recuperacion.max-intentos}") int maxAttempts) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.validMinutes = validMinutes;
        this.maxAttempts = maxAttempts;
    }

    @Override
    @Transactional
    public MessageResponse requestResetCode(ForgotPasswordRequest request) {
        String email = request.correo().trim().toLowerCase();

        userRepository.findByEmail(email)
                .filter(user -> user.getState() == UserState.ACTIVO)
                .ifPresent(this::issueCode);

        return new MessageResponse(GENERIC_MESSAGE);
    }

    @Override
    @Transactional(dontRollbackOn = InvalidResetCodeException.class)
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        if (!request.nueva_contrasena().equals(request.confirmar_contrasena())) {
            throw new PasswordMismatchException();
        }

        String email = request.correo().trim().toLowerCase();

        UserEntity user = userRepository.findByEmail(email)
                .filter(u -> u.getState() == UserState.ACTIVO)
                .orElseThrow(InvalidResetCodeException::new);

        PasswordResetCodeEntity code = codeRepository
                .findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)
                .orElseThrow(InvalidResetCodeException::new);

        if (Instant.now().isAfter(code.getExpiresAt()) || code.getAttempts() >= maxAttempts) {
            throw new InvalidResetCodeException();
        }

        if (!passwordEncoder.matches(request.codigo(), code.getCodeHash())) {
            code.setAttempts(code.getAttempts() + 1);
            codeRepository.save(code);
            log.warn("AUDITORIA accion=RECUPERACION resultado=FALLO usuario={}", user.getId());
            throw new InvalidResetCodeException();
        }

        user.setPassword(passwordEncoder.encode(request.nueva_contrasena()));
        userRepository.save(user);

        code.setUsedAt(Instant.now());
        codeRepository.save(code);

        log.info("AUDITORIA accion=RECUPERACION resultado=EXITO usuario={}", user.getId());
        return new MessageResponse(SUCCESS_MESSAGE);
    }

    private void issueCode(UserEntity user) {
        Instant now = Instant.now();

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
            log.error("No se pudo enviar el código de recuperación al usuario {}", user.getId(), ex);
        }
    }
}