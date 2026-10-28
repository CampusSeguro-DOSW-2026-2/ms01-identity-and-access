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
 * Publica los eventos de auditoría en MS08 a través del API Gateway, de forma asíncrona y sin esperar el
 * resultado, por lo que no bloquea la petición (RNF-MS01-03, RNF-MS01-10, Estándares de Integración,
 * secciones 5 y 12)
 *
 * Envía POST gateway-url/api/v1/audit/events con el cuerpo JSON del evento y las cabeceras
 * X-Service-Key, X-Correlation-ID y, si la acción la ejecuta un usuario autenticado, X-User-Id y
 * X-User-Role sin modificar
 *
 * Si no hay URL del Gateway configurada el evento solo se deja en el log. Cualquier fallo se registra y se
 * descarta, sin reintentos ni colas: nunca afecta al cliente
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
     * Crea el publicador con el cliente HTTP y los datos del Gateway
     *
     * @param httpClient cliente HTTP con el timeout de conexión de las llamadas internas
     * @param gatewayUrl URL base del API Gateway; vacía o nula desactiva el envío (solo log)
     * @param serviceKey clave de servicio de MS01 con la que el Gateway autentica la llamada
     * @param requestTimeout tiempo máximo de espera de la respuesta
     */
    public AuditEventPublisherImpl(HttpClient httpClient, String gatewayUrl, String serviceKey,
            Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.serviceKey = serviceKey;
        this.endpoint = buildEndpoint(gatewayUrl);
    }

    /**
     * Envía el evento al Gateway sin bloquear, o lo deja en el log si el envío está desactivado
     *
     * @param event evento que se emite
     */
    @Override
    public void publish(AuditEvent event) {
        try {
            if (endpoint == null) {
                log.info("Auditoría (Gateway no configurado): accion={} resultado={} usuario={} recurso={} detalle={}",
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
            log.error("No se pudo preparar el evento de auditoría {}/{}: {}", event.accion(), event.resultado(),
                    e.toString());
        }
    }

    /**
     * Agrega a la petición las cabeceras técnicas del evento: la clave de servicio, el correlation id y la
     * identidad del usuario cuando la acción la ejecutó uno autenticado
     *
     * @param request constructor de la petición HTTP
     * @param event evento que se emite
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
     * Registra en los logs el resultado de la petición asíncrona enviada a MS08, informando si
     * ocurrió un error de conexión o si el servidor remoto rechazó el evento, el hilo de respuesta no tiene
     * el correlation id de la petición, por eso se publica en el MDC mientras se escribe el log
     *
     * @param event el evento de auditoría que se intentó reportar
     * @param response la respuesta HTTP recibida por parte de MS08 (puede ser nula si hubo fallo de red)
     * @param error la excepción o error generado durante el envío asíncrono (si lo hubo)
     */
    private void reportOutcome(AuditEvent event, HttpResponse<Void> response, Throwable error) {
        if (event.correlationId() != null) {
            MDC.put(CorrelationIds.MDC_KEY, event.correlationId());
        }
        try {
            if (error != null) {
                log.error("MS08 no recibió el evento de auditoría {}/{}: {}", event.accion(), event.resultado(),
                        error.toString());
            } else if (response.statusCode() >= 300) {
                log.error("MS08 rechazó el evento de auditoría {}/{} con HTTP {}", event.accion(),
                        event.resultado(), response.statusCode());
            }
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

    /**
     * Convierte un evento de auditoría en una cadena con formato JSON con los campos del contrato de
     * auditoría, los campos opcionales sin valor se envían como null
     *
     * @param event el objeto de evento de auditoría a serializar
     * @return una cadena de texto en formato JSON que representa el evento
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
     * Convierte el usuario del evento en texto
     *
     * @param event evento de auditoría
     * @return el id del usuario como texto, o null si el evento es anónimo
     */
    private static String uuidText(AuditEvent event) {
        if (event.usuarioId() == null) {
            return null;
        }
        return event.usuarioId().toString();
    }

    /**
     * Construye la URI del endpoint de eventos combinando la URL del Gateway y la ruta fija del contrato,
     * asegurando que no existan barras duplicadas y manejando el caso en que la URL no esté configurada
     *
     * @param gatewayUrl la URL base del API Gateway
     * @return un objeto URI completamente formado, o null si la URL base está vacía o es nula
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
