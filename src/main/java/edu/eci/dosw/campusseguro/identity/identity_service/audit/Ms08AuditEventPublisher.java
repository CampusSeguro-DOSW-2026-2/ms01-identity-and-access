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
 * Publica los eventos de auditoria en MS08 por HTTP, de forma asincrona, por lo que no bloquea la peticion
 *
 * Envia POST <base-url><events-path> con el cuerpo JSON del evento y la cabecera X-Correlation-ID (RNF-MS01-03)
 * 
 * Si no hay base-url configurada el evento solo se deja en el log. Cualquier fallo se registra y se
 * descarta: nunca afecta al cliente.
 *
 * El cuerpo sigue el contrato de los Estandares (seccion 12): modulo, accion, resultado, usuario_id,
 * recurso_afectado, fecha_hora, correlation_id y detalle. No se envian datos personales distintos de
 * los ids (por eso el correo no viaja). La ruta es configurable (audit.ms08.events-path).
 * 
 */
public class Ms08AuditEventPublisher implements AuditEventPublisher {

    static final String MODULE = "MS01";

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

    /**
     * Registra en los logs el resultado de la petición asíncrona enviada a MS08, informando si
     * ocurrió un error de conexión o si el servidor remoto rechazó el evento
     * 
     * @param event el evento de auditoría que se intentó reportar
     * @param response la respuesta HTTP recibida por parte de MS08 (puede ser nula si hubo fallo de red)
     * @param error la excepción o error generado durante el envío asíncrono (si lo hubo)
     */
    private void reportOutcome(AuditEvent event, HttpResponse<Void> response, Throwable error) {
        if (error != null) {
            log.error("[{}] MS08 no recibió el evento de auditoría {}/{}: {}",
                    event.correlationId(), event.accion(), event.resultado(), error.toString());
        } else if (response.statusCode() >= 300) {
            log.error("[{}] MS08 rechazó el evento de auditoría {}/{} con HTTP {}",
                    event.correlationId(), event.accion(), event.resultado(), response.statusCode());
        }
    }

    /**
     * Convierte un evento de auditoría en una cadena con formato JSON, estructurando los campos
     * obligatorios y opcionales bajo el estándar snake_case en español requerido.
     * 
     * @param event el objeto de evento de auditoría a serializar
     * @return una cadena de texto en formato JSON que representa el evento
     */
    static String toJson(AuditEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modulo", MODULE);
        body.put("accion", event.accion().name());
        body.put("resultado", event.resultado().name());
        body.put("usuario_id", event.usuarioId() == null ? null : event.usuarioId().toString());
        body.put("recurso_afectado", event.recursoAfectado());
        body.put("fecha_hora", DateTimeFormatter.ISO_INSTANT.format(event.fecha()));
        body.put("correlation_id", event.correlationId());
        body.put("detalle", event.detalle());
        return MiniJson.write(body);
    }

    /**
     * Construye y normaliza la URI final del endpoint combinando la URL base y la ruta de eventos, asegurando
     * que no existan barras duplicadas y manejando casos en los que la URL no esté configurada
     * 
     * @param baseUrl la URL base del microservicio MS08
     * @param eventsPath la ruta relativa del recurso de eventos de auditoría
     * @return un objeto URI completamente formado, o null si la URL base está vacía o es nula
     */
    private static URI buildEndpoint(String baseUrl, String eventsPath) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path;
        if (eventsPath == null) {
            path = "";
        } else {
            path = eventsPath.trim();
        }
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(base + path);
    }
}