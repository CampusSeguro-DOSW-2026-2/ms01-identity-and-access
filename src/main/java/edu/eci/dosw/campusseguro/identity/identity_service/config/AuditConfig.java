package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.Ms08AuditEventPublisher;

/**
 * Configuracion de la auditoria hacia MS08 (RNF-MS01-10). No requiere cambios en application.yaml: todo
 * tiene valor por defecto y se ajusta con variables de entorno (o con las propiedades
 * {@code audit.ms08.base-url}, {@code audit.ms08.events-path} y {@code audit.ms08.timeout-ms}).
 *
 * <pre>
 * MS08_BASE_URL     URL base de ms08-analytics-and-audit; vacia = solo se registra en el log
 * MS08_AUDIT_PATH   ruta del recurso de eventos (por defecto /api/v1/audit/events)
 * MS08_TIMEOUT_MS   tiempo maximo de conexion y de respuesta (2000 por defecto)
 * </pre>
 */
@Configuration
public class AuditConfig {

    @Bean
    public AuditEventPublisher auditEventPublisher(
            @Value("${audit.ms08.base-url:${MS08_BASE_URL:}}") String baseUrl,
            @Value("${audit.ms08.events-path:${MS08_AUDIT_PATH:/api/v1/audit/events}}") String eventsPath,
            @Value("${audit.ms08.timeout-ms:${MS08_TIMEOUT_MS:2000}}") long timeoutMs) {

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        return new Ms08AuditEventPublisher(httpClient, baseUrl, eventsPath, Duration.ofMillis(timeoutMs));
    }
}
