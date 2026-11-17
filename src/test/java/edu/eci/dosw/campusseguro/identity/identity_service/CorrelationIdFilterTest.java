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
 * X-Correlation-ID (RNF-MS01-03, integration standards 2.3): it is preserved if it exists, generated if it is missing
 * and returned to the client, the resolved value is the one seen by the controllers and the error handlers
 */
class CorrelationIdFilterTest {

    private static final String VALID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private CorrelationIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private AtomicReference<HttpServletRequest> seenByChain;

        /**
     * Prepares the filter, a request and a chain that stores the request it receives
     */
    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        request = new MockHttpServletRequest("GET", "/api/v1/roles");
        response = new MockHttpServletResponse();
        seenByChain = new AtomicReference<>();
    }

        /**
     * Runs the filter with a chain that only stores the received request
     *
     * @throws Exception if the filter fails
     */
    private void runFilter() throws Exception {
        FilterChain chain = (req, res) -> seenByChain.set((HttpServletRequest) req);
        filter.doFilter(request, response, chain);
    }

        /**
     * A valid received value is kept and returned to the client
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_validHeader_isPreservedAndReturned() throws Exception {
        request.addHeader(CorrelationIds.HEADER, VALID);

        runFilter();

        assertEquals(VALID, response.getHeader(CorrelationIds.HEADER));
        assertEquals(VALID, seenByChain.get().getHeader(CorrelationIds.HEADER));
    }

        /**
     * If the header is missing a UUID is generated, returned to the client and the following steps see the same one
     *
     * @throws Exception if the filter fails
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
     * An invalid value is replaced by a UUID
     *
     * @throws Exception if the filter fails
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
     * The header name is recognized regardless of case
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_headerNameInAnyCase_isResolved() throws Exception {
        runFilter();

        String returned = response.getHeader(CorrelationIds.HEADER);
        assertEquals(returned, seenByChain.get().getHeader("x-correlation-id"));
    }

        /**
     * getHeaders returns a single value, the resolved one
     *
     * @throws Exception if the filter fails
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
     * The header names include X-Correlation-ID even if the client did not send it
     *
     * @throws Exception if the filter fails
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
     * The other headers pass unchanged
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_otherHeaders_areNotModified() throws Exception {
        request.addHeader("X-User-Id", "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");

        runFilter();

        assertEquals("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c", seenByChain.get().getHeader("X-User-Id"));
    }

        /**
     * The resolved value is left as an attribute of the request for the controllers and the /error handler
     *
     * @throws Exception if the filter fails
     */
    @Test
    void doFilter_exposesTheResolvedValueAsRequestAttribute() throws Exception {
        request.addHeader(CorrelationIds.HEADER, VALID);

        runFilter();

        assertEquals(VALID, request.getAttribute(CorrelationIds.ATTRIBUTE));
    }

        /**
     * During the request the value is in the MDC of the log and when it ends it is cleared
     *
     * @throws Exception if the filter fails
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
     * If the chain throws an error, the MDC is also cleared
     */
    @Test
    void doFilter_whenTheChainFails_stillClearsTheMdc() {
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("simulated failure");
        };

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, chain));

        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }
}
