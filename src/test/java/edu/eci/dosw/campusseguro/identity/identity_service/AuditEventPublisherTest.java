package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisherImpl;

/**
 * Publicación asíncrona de eventos de auditoría hacia MS08 a través del Gateway (RNF-MS01-10), contra un
 * servidor HTTP local de pruebas
 */
class AuditEventPublisherTest {

    private static final String PATH = "/api/v1/audit/events";
    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00Z");
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";
    private static final String SERVICE_KEY = "clave-de-servicio";

    /**
     * Petición HTTP que recibió el servidor local de pruebas
     *
     * @param method método HTTP
     * @param path ruta solicitada
     * @param contentType tipo de contenido
     * @param correlationId valor de la cabecera de correlación
     * @param serviceKey valor de la cabecera de llave de servicio
     * @param userId valor de la cabecera de identificador de usuario
     * @param userRole valor de la cabecera de roles
     * @param body cuerpo recibido
     */
    private record Received(String method, String path, String contentType, String correlationId,
            String serviceKey, String userId, String userRole, String body) {
    }

    private HttpServer server;
    private boolean serverStopped;
    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    private volatile int responseStatus = 202;
    private volatile CountDownLatch gate;
    private final CountDownLatch responded = new CountDownLatch(1);

    /**
     * Arranca un servidor HTTP local que hace de API Gateway y guarda las peticiones recibidas
     *
     * @throws IOException si no se puede abrir el puerto local
     */
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PATH, exchange -> {
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                Received request = new Received(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        exchange.getRequestHeaders().getFirst("X-Correlation-ID"),
                        exchange.getRequestHeaders().getFirst("X-Service-Key"),
                        exchange.getRequestHeaders().getFirst("X-User-Id"),
                        exchange.getRequestHeaders().getFirst("X-User-Role"),
                        new String(body, StandardCharsets.UTF_8));
                CountDownLatch latch = gate;
                if (latch != null) {
                    // Respuesta retenida: la peticion se publica antes para que el test pueda observarla.
                    received.add(request);
                    latch.await(10, TimeUnit.SECONDS);
                    exchange.sendResponseHeaders(responseStatus, -1);
                } else {
                    // Se responde primero y se publica despues: cuando el test ve la peticion, la
                    // respuesta ya salio y cerrar el servidor no corta el intercambio.
                    exchange.sendResponseHeaders(responseStatus, -1);
                    received.add(request);
                }
                responded.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    /**
     * Detiene el servidor HTTP local
     */
    @AfterEach
    void stopServer() {
        CountDownLatch latch = gate;
        if (latch != null) {
            latch.countDown();
        }
        if (!serverStopped) {
            serverStopped = true;
            server.stop(0);
        }
    }

    /**
     * Obtiene la URL base del servidor local
     *
     * @return la URL base sin barra final
     */
    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Crea el publicador apuntando a una URL del gateway
     *
     * @param gatewayUrl URL del gateway, puede estar vacía
     * @param serviceKey llave de servicio, puede estar vacía
     * @return el publicador de eventos
     */
    private AuditEventPublisherImpl publisher(String gatewayUrl, String serviceKey) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        return new AuditEventPublisherImpl(client, gatewayUrl, serviceKey, Duration.ofSeconds(2));
    }

    /**
     * Espera la siguiente petición recibida por el servidor local
     *
     * @return la petición recibida
     * @throws InterruptedException si se interrumpe la espera
     */
    private Received awaitRequest() throws InterruptedException {
        Received request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "MS08 no recibio ninguna peticion");
        return request;
    }

    /**
     * Construye un evento de éxito de un usuario autenticado
     *
     * @return el evento de auditoría
     */
    private AuditEvent success() {
        return new AuditEvent(AuditAction.LOGIN, AuditResult.EXITO, USER_ID, null, null, CORRELATION_ID, NOW,
                "usuario:" + USER_ID);
    }

    /**
     * El evento se envía por POST con el cuerpo del contrato y las cabeceras de servicio
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_sendsAPostWithTheContractBodyAndTheServiceHeaders() throws Exception {
        publisher(baseUrl(), SERVICE_KEY).publish(success());

        Received request = awaitRequest();
        assertEquals("POST", request.method());
        assertEquals(PATH, request.path());
        assertEquals("application/json", request.contentType());
        assertEquals(CORRELATION_ID, request.correlationId());
        assertEquals(SERVICE_KEY, request.serviceKey());
        assertEquals("{\"usuario_id\":\"" + USER_ID + "\",\"modulo\":\"MS01\",\"accion\":\"LOGIN\","
                + "\"recurso_afectado\":\"usuario:" + USER_ID + "\",\"resultado\":\"EXITO\","
                + "\"fecha_hora\":\"2026-10-22T05:00:00Z\",\"correlation_id\":\"" + CORRELATION_ID + "\","
                + "\"detalle\":null}", request.body());
    }

    /**
     * Una acción sin usuario autenticado no propaga las cabeceras de identidad
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_actionWithoutAuthenticatedUser_doesNotPropagateTheIdentityHeaders() throws Exception {
        publisher(baseUrl(), SERVICE_KEY).publish(success());

        Received request = awaitRequest();
        assertNull(request.userId());
        assertNull(request.userRole());
    }

    /**
     * Una acción de un usuario autenticado propaga sin cambios las cabeceras de identidad
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_actionByAnAuthenticatedUser_propagatesTheIdentityHeadersUnchanged() throws Exception {
        AuditEvent event = new AuditEvent(AuditAction.CAMBIO_ESTADO, AuditResult.EXITO, USER_ID,
                "ADMIN,AUDITOR", "estado_anterior=ACTIVO;estado_nuevo=INACTIVO", CORRELATION_ID, NOW,
                "usuario:" + UUID.fromString("a0000000-0000-4000-8000-000000000001"));

        publisher(baseUrl(), SERVICE_KEY).publish(event);

        Received request = awaitRequest();
        assertEquals(USER_ID.toString(), request.userId());
        assertEquals("ADMIN,AUDITOR", request.userRole());
        assertTrue(request.body().contains("\"accion\":\"CAMBIO_ESTADO\""), request.body());
        assertTrue(request.body().contains("\"detalle\":\"estado_anterior=ACTIVO;estado_nuevo=INACTIVO\""),
                request.body());
    }

    /**
     * Un intento fallido sin usuario envía nulos explícitos y el motivo
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_failedAttemptWithoutUser_sendsExplicitNullsAndTheReason() throws Exception {
        AuditEvent failure = new AuditEvent(AuditAction.LOGIN, AuditResult.FALLO, null, null,
                "CREDENCIALES_INCORRECTAS", CORRELATION_ID, NOW, null);

        publisher(baseUrl(), SERVICE_KEY).publish(failure);

        String body = awaitRequest().body();
        assertTrue(body.contains("\"resultado\":\"FALLO\""), body);
        assertTrue(body.contains("\"detalle\":\"CREDENCIALES_INCORRECTAS\""), body);
        assertTrue(body.contains("\"usuario_id\":null"), body);
        assertTrue(body.contains("\"recurso_afectado\":null"), body);
    }

    /**
     * El evento nunca incluye contraseñas, hashes, tokens ni correos
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_neverSendsPasswordsHashesTokensOrEmails() throws Exception {
        publisher(baseUrl(), SERVICE_KEY).publish(success());

        String body = awaitRequest().body().toLowerCase();
        for (String forbidden : new String[] { "password", "contrasena", "hash", "token", "codigo", "@" }) {
            assertTrue(!body.contains(forbidden), "El evento no debe contener " + forbidden);
        }
    }

    /**
     * Sin llave de servicio se omite la cabecera
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_withoutServiceKey_omitsTheHeader() throws Exception {
        publisher(baseUrl(), "").publish(success());

        assertNull(awaitRequest().serviceKey());
    }

    /**
     * Se tolera una barra final en la URL del gateway
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_toleratesATrailingSlashInTheGatewayUrl() throws Exception {
        publisher(baseUrl() + "/", SERVICE_KEY).publish(success());

        assertEquals(PATH, awaitRequest().path());
    }

    /**
     * La publicación no espera la respuesta de MS08
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_doesNotWaitForMs08ToAnswer() throws Exception {
        gate = new CountDownLatch(1);
        AuditEventPublisherImpl publisher = publisher(baseUrl(), SERVICE_KEY);

        long start = System.nanoTime();
        publisher.publish(success());
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMs < 1500, "publish() bloqueó " + elapsedMs + " ms esperando a MS08");
        awaitRequest();
        gate.countDown();
        assertTrue(responded.await(5, TimeUnit.SECONDS));
    }

    /**
     * Sin URL del gateway solo se registra en el log y no se envía nada
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_withoutGatewayUrl_onlyLogsAndSendsNothing() throws Exception {
        AuditEventPublisherImpl publisher = publisher("", SERVICE_KEY);

        assertDoesNotThrow(() -> publisher.publish(success()));

        assertNull(received.poll(300, TimeUnit.MILLISECONDS));
    }

    /**
     * Si MS08 responde con un error no se lanza excepción
     *
     * @throws Exception si falla el envío o la lectura de la petición recibida
     */
    @Test
    void publish_whenMs08RespondsWithAnError_doesNotThrow() throws Exception {
        responseStatus = 500;

        assertDoesNotThrow(() -> publisher(baseUrl(), SERVICE_KEY).publish(success()));

        awaitRequest();
    }

    /**
     * Si MS08 no es alcanzable no se lanza excepción
     */
    @Test
    void publish_whenMs08IsUnreachable_doesNotThrow() {
        AuditEventPublisherImpl publisher = publisher(baseUrl(), SERVICE_KEY);
        serverStopped = true;
        server.stop(0);

        assertDoesNotThrow(() -> publisher.publish(success()));
    }
}
