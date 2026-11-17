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
 * Configuration of the audit towards MS08 through the API Gateway (RNF-MS01-03, RNF-MS01-10), it is adjusted with
 * environment variables (or with the properties gateway.url and gateway.service-key)
 *
 * GATEWAY_URL base URL of the API Gateway; empty = it is only logged
 * GATEWAY_SERVICE_KEY service key of MS01 (random, 32 characters or more), never in the repository
 *
 * The maximum connection and response time is a global value of the Integration Standards (section 1) and it is defined
 * in the code, it is not configured
 */
@Configuration
public class AuditConfig {

        /**
     * Creates and registers a bean of type AuditEventPublisher configured to send audit events by HTTP to MS08 through
     * the Gateway, with an HTTP client with a bounded timeout
     *
     * @param gatewayUrl the base URL of the API Gateway (it may come from properties or environment variables)
     * @param serviceKey the service key of MS01 with which the Gateway authenticates the call
     * @return a configured instance of AuditEventPublisherImpl as a bean managed by Spring
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
