package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisherImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;

/**
 * Configuración de la auditoría hacia MS08 a través del API Gateway (RNF-MS01-03, RNF-MS01-10), se ajusta
 * con variables de entorno (o con las propiedades gateway.url y gateway.service-key)
 *
 * GATEWAY_URL          URL base del API Gateway; vacía = solo se registra en el log
 * GATEWAY_SERVICE_KEY  clave de servicio de MS01 (aleatoria, de 32 caracteres o más), nunca en el repositorio
 *
 * El tiempo máximo de conexión y de respuesta es un valor global de los Estándares de Integración
 * (sección 1) y se define en el código, no se configura
 */
@Configuration
public class AuditConfig {

    /**
     * Crea y registra un bean de tipo AuditEventPublisher configurado para enviar eventos de auditoría
     * por HTTP hacia MS08 a través del Gateway, con un cliente HTTP de timeout acotado
     *
     * @param gatewayUrl la URL base del API Gateway (puede provenir de propiedades o variables de entorno)
     * @param serviceKey la clave de servicio de MS01 con la que el Gateway autentica la llamada
     * @return una instancia configurada de AuditEventPublisherImpl como un bean gestionado por Spring
     */
    @Bean
    public AuditEventPublisher auditEventPublisher(
            @Value("${gateway.url:${GATEWAY_URL:}}") String gatewayUrl,
            @Value("${gateway.service-key:${GATEWAY_SERVICE_KEY:}}") String serviceKey) {

        Duration timeout = Duration.ofMillis(GlobalValues.INTERNAL_CLIENT_TIMEOUT_MS);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        return new AuditEventPublisherImpl(httpClient, gatewayUrl, serviceKey, timeout);
    }
}
