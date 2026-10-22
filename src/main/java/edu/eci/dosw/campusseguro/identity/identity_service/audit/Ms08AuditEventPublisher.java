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

import edu.eci.dosw.campusseguro.identity.identity_service.util.MiniJson;

/**
 * Publica los eventos de auditoria en MS08 por HTTP, de forma asincrona (no bloquea la peticion).
 *
 * Envia {@code POST <base-url><events-path>} con el cuerpo JSON del evento y la cabecera
 * {@code X-Correlation-ID} (RNF-MS01-03). Si no hay base-url configurada (desarrollo local) el
 * evento solo se deja en el log. Cualquier fallo se registra y se descarta: nunca afecta al cliente.
 *
 * Nota: el contrato HTTP de MS08 no esta en los requerimientos de MS01; la ruta es configurable
 * (audit.ms08.events-path) y el cuerpo usa los mismos nombres snake_case en espanol del resto de la API.
 */
public class Ms08AuditEventPublisher implements AuditEventPublisher {

    static final String SOURCE = "ms01-identity-and-access";

    private static final Logger log = LoggerFactory.getLogger(Ms08AuditEventPublisher.class);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration requestTimeout;

    /**
     * @param baseUrl  URL base de MS08; vacia o nula desactiva el envio (solo log)
     * @param eventsPath ruta del recurso de eventos, p. ej. /api/v1/audit/events
     */
    public Ms08AuditEventPublisher(HttpClient httpClient, String baseUrl, String eventsPath,
            Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.endpoint = buildEndpoint(baseUrl, eventsPath);
    }

    @Override
    public void publish(AuditEvent event) {
        try {
            if (endpoint == null) {
                log.info("[{}] Auditoría (MS08 no configurado): accion={} resultado={} usuario={} detalle={}",
                        event.correlationId(), event.accion(), event.resultado(), event.usuarioId(),
                        event.detalle());
                return;
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(event), StandardCharsets.UTF_8));
            if (event.correlationId() != null) {
                request.header("X-Correlation-ID", event.correlationId());
            }
            httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> reportOutcome(event, response, error));
        } catch (RuntimeException e) {
            log.error("[{}] No se pudo preparar el evento de auditoría {}/{}: {}",
                    event.correlationId(), event.accion(), event.resultado(), e.toString());
        }
    }

    private void reportOutcome(AuditEvent event, HttpResponse<Void> response, Throwable error) {
        if (error != null) {
            log.error("[{}] MS08 no recibió el evento de auditoría {}/{}: {}",
                    event.correlationId(), event.accion(), event.resultado(), error.toString());
        } else if (response.statusCode() >= 300) {
            log.error("[{}] MS08 rechazó el evento de auditoría {}/{} con HTTP {}",
                    event.correlationId(), event.accion(), event.resultado(), response.statusCode());
        }
    }

    static String toJson(AuditEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origen", SOURCE);
        body.put("accion", event.accion().name());
        body.put("resultado", event.resultado().name());
        if (event.usuarioId() != null) {
            body.put("usuario_id", event.usuarioId().toString());
        }
        if (event.correo() != null) {
            body.put("correo", event.correo());
        }
        if (event.detalle() != null) {
            body.put("detalle", event.detalle());
        }
        if (event.correlationId() != null) {
            body.put("correlation_id", event.correlationId());
        }
        body.put("fecha", DateTimeFormatter.ISO_INSTANT.format(event.fecha()));
        return MiniJson.write(body);
    }

    private static URI buildEndpoint(String baseUrl, String eventsPath) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = (eventsPath == null) ? "" : eventsPath.trim();
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(base + path);
    }
}
