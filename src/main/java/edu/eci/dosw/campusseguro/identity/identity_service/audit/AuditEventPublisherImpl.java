package edu.eci.dosw.campusseguro.identity.identity_service.audit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.util.MiniJson;

/**
 * Publishes the audit events in MS08 through the API Gateway, asynchronously and without waiting for the result, so it
 * does not block the request (RNF-MS01-03, RNF-MS01-10, Integration Standards, sections 5 and 12)
 *
 * Sends POST gateway-url/api/v1/audit/events with the JSON body of the event and the headers X-Service-Key,
 * X-Correlation-ID and, if the action is executed by an authenticated user, X-User-Id and X-User-Role unmodified
 *
 * If no Gateway URL is configured the event is only left in the log. Any failure is logged and discarded, without
 * retries or queues: it never affects the client
 */
public class AuditEventPublisherImpl implements AuditEventPublisher {

    static final String MODULE = "MS01";
    static final String EVENTS_PATH = "/api/v1/audit/events";
    static final String SERVICE_KEY_HEADER = "X-Service-Key";
    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisherImpl.class);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String serviceKey;
    private final Duration requestTimeout;

        /**
     * Creates the publisher with the HTTP client and the data of the Gateway
     *
     * @param httpClient HTTP client with the connection timeout of the internal calls
     * @param gatewayUrl base URL of the API Gateway; empty or null disables the sending (log only)
     * @param serviceKey service key of MS01 with which the Gateway authenticates the call
     * @param requestTimeout maximum time to wait for the response
     */
    public AuditEventPublisherImpl(HttpClient httpClient, String gatewayUrl, String serviceKey,
            Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.serviceKey = serviceKey;
        this.endpoint = buildEndpoint(gatewayUrl);
    }

        /**
     * Sends the event to the Gateway without blocking, or leaves it in the log if the sending is disabled
     *
     * @param event event that is emitted
     */
    @Override
    public void publish(AuditEvent event) {
        try {
            if (endpoint == null) {
                log.info("Audit (Gateway not configured): action={} result={} user={} resource={} detail={}",
                        event.accion(), event.resultado(), event.usuarioId(), event.recursoAfectado(),
                        event.detalle());
                return;
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(event), StandardCharsets.UTF_8));
            addHeaders(request, event);
            httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> reportOutcome(event, response, error));
        } catch (RuntimeException e) {
            log.error("The audit event {}/{} could not be prepared: {}", event.accion(), event.resultado(),
                    e.toString());
        }
    }

        /**
     * Adds to the request the technical headers of the event: the service key, the correlation id and the identity of
     * the user when the action was executed by an authenticated one
     *
     * @param request builder of the HTTP request
     * @param event event that is emitted
     */
    private void addHeaders(HttpRequest.Builder request, AuditEvent event) {
        if (serviceKey != null && !serviceKey.isBlank()) {
            request.header(SERVICE_KEY_HEADER, serviceKey);
        }
        if (event.correlationId() != null) {
            request.header(CorrelationIds.HEADER, event.correlationId());
        }
        if (event.usuarioId() != null && event.usuarioRoles() != null) {
            request.header(USER_ID_HEADER, event.usuarioId().toString());
            request.header(USER_ROLE_HEADER, event.usuarioRoles());
        }
    }

        /**
     * Logs the result of the asynchronous request sent to MS08, reporting whether a connection error occurred or the
     * remote server rejected the event, the response thread does not have the correlation id of the request, so it is
     * published in the MDC while the log is written
     *
     * @param event the audit event that was attempted to be reported
     * @param response the HTTP response received from MS08 (may be null if there was a network failure)
     * @param error the exception or error generated during the asynchronous sending (if there was one)
     */
    private void reportOutcome(AuditEvent event, HttpResponse<Void> response, Throwable error) {
        if (event.correlationId() != null) {
            MDC.put(CorrelationIds.MDC_KEY, event.correlationId());
        }
        try {
            if (error != null) {
                log.error("MS08 did not receive the audit event {}/{}: {}", event.accion(), event.resultado(),
                        error.toString());
            } else if (response.statusCode() >= 300) {
                log.error("MS08 rejected the audit event {}/{} with HTTP {}", event.accion(),
                        event.resultado(), response.statusCode());
            }
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

        /**
     * Converts an audit event into a string in JSON format with the fields of the audit contract, the optional fields
     * without value are sent as null
     *
     * @param event the audit event object to serialize
     * @return a text string in JSON format that represents the event
     */
    static String toJson(AuditEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("usuario_id", uuidText(event));
        body.put("modulo", MODULE);
        body.put("accion", event.accion().name());
        body.put("recurso_afectado", event.recursoAfectado());
        body.put("resultado", event.resultado().name());
        body.put("fecha_hora", DateTimeFormatter.ISO_INSTANT.format(event.fecha()));
        body.put("correlation_id", event.correlationId());
        body.put("detalle", event.detalle());
        return MiniJson.write(body);
    }

        /**
     * Converts the user of the event to text
     *
     * @param event audit event
     * @return the id of the user as text, or null if the event is anonymous
     */
    private static String uuidText(AuditEvent event) {
        if (event.usuarioId() == null) {
            return null;
        }
        return event.usuarioId().toString();
    }

        /**
     * Builds the URI of the events endpoint by combining the Gateway URL and the fixed route of the contract, making
     * sure there are no duplicated slashes and handling the case in which the URL is not configured
     *
     * @param gatewayUrl the base URL of the API Gateway
     * @return a fully formed URI object, or null if the base URL is empty or null
     */
    private static URI buildEndpoint(String gatewayUrl) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            return null;
        }
        String base = gatewayUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + EVENTS_PATH);
    }
}
