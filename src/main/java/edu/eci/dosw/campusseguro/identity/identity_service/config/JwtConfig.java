package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.security.PrivateKey;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtService;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RsaKeyUtils;
import edu.eci.dosw.campusseguro.identity.identity_service.service.DatabaseTimeService;

/**
 * Configuration of the JWT issuer (RF-MS01-03, 3_jwt-spec.md). The secrets arrive through environment variables and are
 * never stored in the repository (RNF-MS01-06). It requires no changes in application.yaml: Spring resolves
 * jwt.private-key from JWT_PRIVATE_KEY and jwt.key-id from JWT_KEY_ID
 *
 * JWT_PRIVATE_KEY RSA private key in PEM PKCS#8 JWT_KEY_ID identifier of the key
 *
 * The issuer, the audience and the validity of the token are global values of the Integration Standards (section 1) and
 * they are defined in the code, they are not configured
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

        /**
     * Provides a Clock bean configured in coordinated universal time (UTC)
     * for the standardized handling of dates and times in the issuance and validation of tokens
     *
     * @return a Clock instance that uses UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

        /**
     * Creates and registers a JwtService bean in charge of generating and digitally signing the JWTs, configuring the
     * private key or generating an RSA pair for development and the corresponding issuance metadata
     *
     * @param privateKeyPem the text string with the private key in PEM PKCS#8 format
     * @param keyId the unique identifier of the cryptographic key (Key ID)
     * @param timeService the single database clock with which the iat and the expiration are calculated
     * @return a configured instance of JwtServiceImpl as a bean managed by Spring
     */
    @Bean
    public JwtService jwtService(
            @Value("${jwt.private-key:}") String privateKeyPem,
            @Value("${jwt.key-id:ms01-2026-01}") String keyId,
            DatabaseTimeService timeService) {

        PrivateKey privateKey;
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            log.warn("JWT_PRIVATE_KEY is not configured: an ephemeral RSA key pair generated at startup is used. "
                    + "The tokens will stop being valid on restart and the API Gateway will not be able to "
                    + "verify them. Use it only in development.");
            privateKey = RsaKeyUtils.generateKeyPair().getPrivate();
        } else {
            privateKey = RsaKeyUtils.parsePrivateKey(privateKeyPem);
        }
        return new JwtServiceImpl(privateKey, keyId, timeService);
    }
}
