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

/**
 * Configuracion del emisor de JWT (RF-MS01-03, 3_jwt-spec.md). Los secretos llegan por variables de
 * entorno y nunca se guardan en el repositorio (RNF-MS01-06). No requiere cambios en application.yaml:
 * Spring resuelve jwt.private-key desde JWT_PRIVATE_KEY y jwt.key-id desde JWT_KEY_ID
 *
 * JWT_PRIVATE_KEY llave privada RSA en PEM PKCS#8
 * JWT_KEY_ID identificador de la llave
 *
 * El emisor, la audiencia y la vigencia del token son valores globales de los Estándares de Integración
 * (sección 1) y se definen en el código, no se configuran
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    /**
     * Provee un bean de tipo Clock configurado en tiempo universal coordinado (UTC)
     * para el manejo estandarizado de fechas y horas en la emisión y validación de tokens
     *
     * @return una instancia de Clock que usa el formato UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Crea y registra un bean de tipo JwtService encargado de la generación y firma digital de los JWT,
     * configurando la llave privada o generando un par RSA para desarrollo y los metadatos de emisión
     * correspondientes
     *
     * @param privateKeyPem la cadena de texto con la llave privada en formato PEM PKCS#8
     * @param keyId el identificador único de la llave criptográfica (Key ID)
     * @param clock el reloj UTC inyectado para el control temporal de expiración
     * @return una instancia configurada de JwtServiceImpl como un bean gestionado por Spring
     */
    @Bean
    public JwtService jwtService(
            @Value("${jwt.private-key:}") String privateKeyPem,
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
        return new JwtServiceImpl(privateKey, keyId, clock);
    }
}
