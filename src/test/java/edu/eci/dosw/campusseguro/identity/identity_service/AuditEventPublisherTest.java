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
 * Asynchronous publication of audit events to MS08 through the Gateway (RNF-MS01-10), against a local HTTP test server
 */
class AuditEventPublisherTest {

    private static final String PATH = "/api/v1/audit/events";
    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00Z");
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";
    private static final String SERVICE_KEY = "clave-de-servicio";

        /**
     * HTTP request received by the local test server
     *
     * @param method HTTP method
     * @param path requested path
     * @param contentType content type
     * @param correlationId value of the correlation header
     * @param serviceKey value of the service key header
     * @param userId value of the user identifier header
     * @param userRole value of the roles header
     * @param body body received
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
     * Starts a local HTTP server that acts as the API Gateway and stores the received requests
     *
     * @throws IOException if the local port cannot be opened
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
                    // Held response: the request is published beforehand so the test can observe it.
                    received.add(request);
                    latch.await(10, TimeUnit.SECONDS);
                    exchange.sendResponseHeaders(responseStatus, -1);
                } else {
                    // The response is sent first and the request is published afterwards: when the test sees the
                    // request, the response has already gone out and closing the server does not cut the exchange.
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
     * Stops the local HTTP server
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
     * Gets the base URL of the local server
     *
     * @return the base URL without a trailing slash
     */
    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

        /**
     * Creates the publisher pointing to a gateway URL
     *
     * @param gatewayUrl gateway URL, may be empty
     * @param serviceKey service key, may be empty
     * @return the event publisher
     */
    private AuditEventPublisherImpl publisher(String gatewayUrl, String serviceKey) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        return new AuditEventPublisherImpl(client, gatewayUrl, serviceKey, Duration.ofSeconds(2));
    }

        /**
     * Waits for the next request received by the local server
     *
     * @return the received request
     * @throws InterruptedException if the wait is interrupted
     */
    private Received awaitRequest() throws InterruptedException {
        Received request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "MS08 no recibio ninguna peticion");
        return request;
    }

        /**
     * Builds a success event of an authenticated user
     *
     * @return the audit event
     */
    private AuditEvent success() {
        return new AuditEvent(AuditAction.LOGIN, AuditResult.EXITO, USER_ID, null, null, CORRELATION_ID, NOW,
                "usuario:" + USER_ID);
    }

        /**
     * The event is sent by POST with the body of the contract and the service headers
     *
     * @throws Exception if sending fails or reading the received request fails
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
     * An action without an authenticated user does not propagate the identity headers
     *
     * @throws Exception if sending fails or reading the received request fails
     */
    @Test
    void publish_actionWithoutAuthenticatedUser_doesNotPropagateTheIdentityHeaders() throws Exception {
        publisher(baseUrl(), SERVICE_KEY).publish(success());

        Received request = awaitRequest();
        assertNull(request.userId());
        assertNull(request.userRole());
    }

        /**
     * An action of an authenticated user propagates the identity headers unchanged
     *
     * @throws Exception if sending fails or reading the received request fails
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
     * A failed attempt without a user sends explicit nulls and the reason
     *
     * @throws Exception if sending fails or reading the received request fails
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
     * The event never includes passwords, hashes, tokens or emails
     *
     * @throws Exception if sending fails or reading the received request fails
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
     * Without a service key the header is omitted
     *
     * @throws Exception if sending fails or reading the received request fails
     */
    @Test
    void publish_withoutServiceKey_omitsTheHeader() throws Exception {
        publisher(baseUrl(), "").publish(success());

        assertNull(awaitRequest().serviceKey());
    }

        /**
     * A trailing slash in the gateway URL is tolerated
     *
     * @throws Exception if sending fails or reading the received request fails
     */
    @Test
    void publish_toleratesATrailingSlashInTheGatewayUrl() throws Exception {
        publisher(baseUrl() + "/", SERVICE_KEY).publish(success());

        assertEquals(PATH, awaitRequest().path());
    }

        /**
     * The publication does not wait for the response of MS08
     *
     * @throws Exception if sending fails or reading the received request fails
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
     * Without a gateway URL it is only logged and nothing is sent
     *
     * @throws Exception if sending fails or reading the received request fails
     */
    @Test
    void publish_withoutGatewayUrl_onlyLogsAndSendsNothing() throws Exception {
        AuditEventPublisherImpl publisher = publisher("", SERVICE_KEY);

        assertDoesNotThrow(() -> publisher.publish(success()));

        assertNull(received.poll(300, TimeUnit.MILLISECONDS));
    }

        /**
     * If MS08 answers with an error no exception is thrown
     *
     * @throws Exception if sending fails or reading the received request fails
     */
    @Test
    void publish_whenMs08RespondsWithAnError_doesNotThrow() throws Exception {
        responseStatus = 500;

        assertDoesNotThrow(() -> publisher(baseUrl(), SERVICE_KEY).publish(success()));

        awaitRequest();
    }

        /**
     * If MS08 is not reachable no exception is thrown
     */
    @Test
    void publish_whenMs08IsUnreachable_doesNotThrow() {
        AuditEventPublisherImpl publisher = publisher(baseUrl(), SERVICE_KEY);
        serverStopped = true;
        server.stop(0);

        assertDoesNotThrow(() -> publisher.publish(success()));
    }
}
