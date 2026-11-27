package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import com.jayway.jsonpath.JsonPath;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.security.ForbiddenAccessDeniedHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * An access denied by the security layer answers 403 with the ErrorResponse of the integration standards
 */
class ForbiddenAccessDeniedHandlerTest {

    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private ForbiddenAccessDeniedHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

        /**
     * Prepares the handler, an empty request and an empty response
     */
    @BeforeEach
    void setUp() {
        handler = new ForbiddenAccessDeniedHandler();
        request = new MockHttpServletRequest("GET", "/api/v1/users");
        response = new MockHttpServletResponse();
    }

        /**
     * The response is 403 in JSON with the code and the message of the catalog
     *
     * @throws Exception if writing the response fails
     */
    @Test
    void handle_returns403WithTheCatalogBody() throws Exception {
        request.addHeader(CorrelationIds.HEADER, CORRELATION_ID);

        handler.handle(request, response, new AccessDeniedException("denied"));

        String body = response.getContentAsString();
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals(403, JsonPath.<Integer>read(body, "$.status"));
        assertEquals("PROHIBIDO", JsonPath.<String>read(body, "$.codigo"));
        assertEquals(ErrorCode.FORBIDDEN.getMessage(), JsonPath.<String>read(body, "$.mensaje"));
        assertEquals(List.of(), JsonPath.<List<Object>>read(body, "$.errores"));
        assertEquals(CORRELATION_ID, JsonPath.<String>read(body, "$.correlation_id"));
    }

        /**
     * The correlation id is also returned in the header of the response
     *
     * @throws Exception if writing the response fails
     */
    @Test
    void handle_returnsTheCorrelationIdHeader() throws Exception {
        request.addHeader(CorrelationIds.HEADER, CORRELATION_ID);

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertEquals(CORRELATION_ID, response.getHeader(CorrelationIds.HEADER));
    }
}
