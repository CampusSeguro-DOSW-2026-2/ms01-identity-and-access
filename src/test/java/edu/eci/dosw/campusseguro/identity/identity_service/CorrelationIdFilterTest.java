package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import edu.eci.dosw.campusseguro.identity.identity_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;

/**
 * X-Correlation-ID (RNF-MS01-03, estandares de integracion 2.3): se preserva si existe, se genera si falta
 * y se devuelve al cliente, el valor resuelto es el que ven los controladores y los manejadores de error
 */
class CorrelationIdFilterTest {

    private static final String VALID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private CorrelationIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private AtomicReference<HttpServletRequest> seenByChain;

    /**
     * Prepara el filtro, una peticion y una cadena que guarda la peticion que recibe
     */
    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        request = new MockHttpServletRequest("GET", "/api/v1/roles");
        response = new MockHttpServletResponse();
        seenByChain = new AtomicReference<>();
    }

    /**
     * Ejecuta el filtro con una cadena que solo guarda la peticion recibida
     *
     * @throws Exception si falla el filtro
     */
    private void runFilter() throws Exception {
        FilterChain chain = (req, res) -> seenByChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);
    }

    /**
     * Un valor recibido valido se conserva y se devuelve al cliente
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_validHeader_isPreservedAndReturned() throws Exception {
        request.addHeader(CorrelationIds.HEADER, VALID);

        runFilter();

        assertEquals(VALID, response.getHeader(CorrelationIds.HEADER));
        assertEquals(VALID, seenByChain.get().getHeader(CorrelationIds.HEADER));
    }

    /**
     * Si la cabecera falta se genera un UUID, se devuelve al cliente y los siguientes pasos ven el mismo
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_missingHeader_generatesAUuidAndExposesItToTheChain() throws Exception {
        runFilter();

        String returned = response.getHeader(CorrelationIds.HEADER);
        assertNotNull(returned);
        assertDoesNotThrow(() -> UUID.fromString(returned));
        assertEquals(returned, seenByChain.get().getHeader(CorrelationIds.HEADER));
    }

    /**
     * Un valor no valido se reemplaza por un UUID
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_unsafeHeader_isReplacedByAUuid() throws Exception {
        request.addHeader(CorrelationIds.HEADER, "abc def");

        runFilter();

        String returned = response.getHeader(CorrelationIds.HEADER);
        assertDoesNotThrow(() -> UUID.fromString(returned));
        assertEquals(returned, seenByChain.get().getHeader(CorrelationIds.HEADER));
    }

    /**
     * El nombre de la cabecera se reconoce sin distinguir mayusculas
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_headerNameInAnyCase_isResolved() throws Exception {
        runFilter();

        String returned = response.getHeader(CorrelationIds.HEADER);
        assertEquals(returned, seenByChain.get().getHeader("x-correlation-id"));
    }

    /**
     * getHeaders devuelve un solo valor, el resuelto
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_getHeaders_returnsOnlyTheResolvedValue() throws Exception {
        request.addHeader(CorrelationIds.HEADER, VALID);
        request.addHeader(CorrelationIds.HEADER, "1c2f9d34-5b7a-4e0c-9d61-7a4b8e3f2a10");

        runFilter();

        List<String> values = Collections.list(seenByChain.get().getHeaders(CorrelationIds.HEADER));
        assertEquals(List.of(VALID), values);
    }

    /**
     * Los nombres de cabecera incluyen X-Correlation-ID aunque el cliente no la haya enviado
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_getHeaderNames_includesTheCorrelationHeader() throws Exception {
        request.addHeader("X-User-Id", "x");

        runFilter();

        List<String> names = Collections.list(seenByChain.get().getHeaderNames());
        assertTrue(names.contains(CorrelationIds.HEADER));
        assertTrue(names.contains("X-User-Id"));
    }

    /**
     * Las demas cabeceras pasan sin cambios
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_otherHeaders_areNotModified() throws Exception {
        request.addHeader("X-User-Id", "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");

        runFilter();

        assertEquals("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c", seenByChain.get().getHeader("X-User-Id"));
    }

    /**
     * El valor resuelto queda como atributo de la petición para los controladores y el manejador de /error
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_exposesTheResolvedValueAsRequestAttribute() throws Exception {
        request.addHeader(CorrelationIds.HEADER, VALID);

        runFilter();

        assertEquals(VALID, request.getAttribute(CorrelationIds.ATTRIBUTE));
    }

    /**
     * Durante la petición el valor está en el MDC del log y al terminar se limpia
     *
     * @throws Exception si falla el filtro
     */
    @Test
    void doFilter_putsTheValueInTheMdcOnlyWhileTheRequestRuns() throws Exception {
        request.addHeader(CorrelationIds.HEADER, VALID);
        AtomicReference<String> inside = new AtomicReference<>();
        FilterChain chain = (req, res) -> inside.set(MDC.get(CorrelationIds.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertEquals(VALID, inside.get());
        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }

    /**
     * Si la cadena lanza un error, el MDC también se limpia
     */
    @Test
    void doFilter_whenTheChainFails_stillClearsTheMdc() {
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("fallo simulado");
        };

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, chain));

        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }
}
