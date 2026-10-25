package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        request.addHeader(CorrelationIds.HEADER, "c19b-472d-a12b");

        runFilter();

        assertEquals("c19b-472d-a12b", response.getHeader(CorrelationIds.HEADER));
        assertEquals("c19b-472d-a12b", seenByChain.get().getHeader(CorrelationIds.HEADER));
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
        request.addHeader(CorrelationIds.HEADER, "uno");
        request.addHeader(CorrelationIds.HEADER, "dos");

        runFilter();

        List<String> values = Collections.list(seenByChain.get().getHeaders(CorrelationIds.HEADER));
        assertEquals(List.of("uno"), values);
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
}
