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
import edu.eci.dosw.campusseguro.identity.identity_service.audit.Ms08AuditEventPublisher;

/**
 * Publicacion asincrona de eventos de auditoria hacia MS08 (RNF-MS01-10), contra un servidor HTTP
 * local de pruebas.
 */
class Ms08AuditEventPublisherTest {

    private static final String PATH = "/api/v1/audit/events";
    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00Z");

    private record Received(String method, String path, String contentType, String correlationId, String body) {
    }

    private HttpServer server;
    private boolean serverStopped;
    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    private volatile int responseStatus = 202;
    private volatile CountDownLatch gate;
    private final CountDownLatch responded = new CountDownLatch(1);

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

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private Ms08AuditEventPublisher publisher(String baseUrl, String path) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        return new Ms08AuditEventPublisher(client, baseUrl, path, Duration.ofSeconds(2));
    }

    private Received awaitRequest() throws InterruptedException {
        Received request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "MS08 no recibio ninguna peticion");
        return request;
    }

    private AuditEvent success() {
        return new AuditEvent(AuditAction.LOGIN, AuditResult.EXITO, USER_ID, "maria.perez@escuelaing.edu.co",
                null, "corr-1", NOW);
    }

    @Test
    void publish_sendsAPostWithJsonBodyAndCorrelationHeader() throws Exception {
        publisher(baseUrl(), PATH).publish(success());

        Received request = awaitRequest();
        assertEquals("POST", request.method());
        assertEquals(PATH, request.path());
        assertEquals("application/json", request.contentType());
        assertEquals("corr-1", request.correlationId());
        assertEquals("{\"origen\":\"ms01-identity-and-access\",\"accion\":\"LOGIN\",\"resultado\":\"EXITO\","
                + "\"usuario_id\":\"" + USER_ID + "\",\"correo\":\"maria.perez@escuelaing.edu.co\","
                + "\"correlation_id\":\"corr-1\",\"fecha\":\"2026-10-22T05:00:00Z\"}", request.body());
    }

    @Test
    void publish_failedAttemptIncludesTheReasonAndOmitsUnknownUser() throws Exception {
        AuditEvent failure = new AuditEvent(AuditAction.LOGIN, AuditResult.FALLO, null, "nadie@escuelaing.edu.co",
                "CREDENCIALES_INCORRECTAS", "corr-2", NOW);

        publisher(baseUrl(), PATH).publish(failure);

        String body = awaitRequest().body();
        assertTrue(body.contains("\"resultado\":\"FALLO\""), body);
        assertTrue(body.contains("\"detalle\":\"CREDENCIALES_INCORRECTAS\""), body);
        assertTrue(!body.contains("usuario_id"), body);
    }

    @Test
    void publish_neverSendsPasswordsHashesOrTokens() throws Exception {
        publisher(baseUrl(), PATH).publish(success());

        String body = awaitRequest().body().toLowerCase();
        for (String forbidden : new String[] { "password", "contrasena", "hash", "token", "codigo" }) {
            assertTrue(!body.contains(forbidden), "El evento no debe contener " + forbidden);
        }
    }

    @Test
    void publish_toleratesTrailingSlashAndMissingLeadingSlash() throws Exception {
        publisher(baseUrl() + "/", "api/v1/audit/events").publish(success());

        assertEquals(PATH, awaitRequest().path());
    }

    @Test
    void publish_doesNotWaitForMs08ToAnswer() throws Exception {
        gate = new CountDownLatch(1);
        Ms08AuditEventPublisher publisher = publisher(baseUrl(), PATH);

        long start = System.nanoTime();
        publisher.publish(success());
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMs < 1500, "publish() bloqueo " + elapsedMs + " ms esperando a MS08");
        awaitRequest();
        gate.countDown();
        assertTrue(responded.await(5, TimeUnit.SECONDS));
    }

    @Test
    void publish_withoutBaseUrl_onlyLogsAndSendsNothing() throws Exception {
        Ms08AuditEventPublisher publisher = publisher("", PATH);

        assertDoesNotThrow(() -> publisher.publish(success()));

        assertNull(received.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void publish_whenMs08RespondsWithAnError_doesNotThrow() throws Exception {
        responseStatus = 500;

        assertDoesNotThrow(() -> publisher(baseUrl(), PATH).publish(success()));

        awaitRequest();
    }

    @Test
    void publish_whenMs08IsUnreachable_doesNotThrow() {
        Ms08AuditEventPublisher publisher = publisher(baseUrl(), PATH);
        serverStopped = true;
        server.stop(0);

        assertDoesNotThrow(() -> publisher.publish(success()));
    }
}
