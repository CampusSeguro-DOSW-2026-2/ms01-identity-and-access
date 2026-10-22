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
 * tiene valor por defecto y se ajusta con variables de entorno (o con las propiedades; audit.ms08.base-url,
 * audit.ms08.events-path y audit.ms08.timeout-ms).
 * 
 * MS08_BASE_URL     URL base de ms08-analytics-and-audit; vacia = solo se registra en el log
 * MS08_AUDIT_PATH   ruta del recurso de eventos (por defecto /api/v1/audit/events)
 * MS08_TIMEOUT_MS   tiempo maximo de conexion y de respuesta (2000 por defecto)
 */
@Configuration
public class AuditConfig {

    /**
     * Crea y registra un bean de tipo AuditEventPublisher configurado para enviar 
     * eventos de auditoría por HTTP hacia el microservicio MS08, utilizando un cliente HTTP optimizado 
     * y parámetros de conexión dinámicos.
     * 
     * @param baseUrl la URL base del microservicio MS08 (puede provenir de propiedades o variables de entorno)
     * @param eventsPath la ruta relativa del recurso donde se publican los eventos de auditoría
     * @param timeoutMs el tiempo máximo de espera en milisegundos para las conexiones y respuestas HTTP
     * @return una instancia configurada de Ms08AuditEventPublisher como un bean gestionado por Spring
     */
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
