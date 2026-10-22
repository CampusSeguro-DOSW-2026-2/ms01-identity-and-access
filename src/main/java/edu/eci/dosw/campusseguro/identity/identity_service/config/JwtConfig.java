package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtService;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RsaKeyUtils;

/**
 * Configuracion del emisor de JWT (RF-MS01-03, 4_jwt-spec.md). Los secretos llegan por variables de
 * entorno y nunca se guardan en el repositorio (RNF-MS01-06). No requiere cambios en application.yaml:
 * Spring resuelve {@code jwt.private-key} desde {@code JWT_PRIVATE_KEY}, {@code jwt.expiration-minutes}
 * desde {@code JWT_EXPIRATION_MINUTES}, y asi con el resto.
 *
 * <pre>
 * JWT_PRIVATE_KEY         llave privada RSA en PEM PKCS#8
 * JWT_EXPIRATION_MINUTES  vigencia del token (60 por defecto)
 * JWT_ISSUER / JWT_AUDIENCE / JWT_KEY_ID
 * </pre>
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public JwtService jwtService(
            @Value("${jwt.private-key:}") String privateKeyPem,
            @Value("${jwt.expiration-minutes:60}") long expirationMinutes,
            @Value("${jwt.issuer:ms01-identity-and-access}") String issuer,
            @Value("${jwt.audience:campusseguro}") String audience,
            @Value("${jwt.key-id:ms01-2026-01}") String keyId,
            Clock clock) {

        PrivateKey privateKey;
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            log.warn("JWT_PRIVATE_KEY no está configurada: se usa un par de llaves RSA efímero generado al arrancar. "
                    + "Los tokens dejarán de ser válidos al reiniciar y el API Gateway no podrá verificarlos. "
                    + "Úselo solo en desarrollo.");
            privateKey = RsaKeyUtils.generateKeyPair().getPrivate();
        } else {
            privateKey = RsaKeyUtils.parsePrivateKey(privateKeyPem);
        }
        return new JwtServiceImpl(privateKey, issuer, audience, keyId, Duration.ofMinutes(expirationMinutes), clock);
    }
}
