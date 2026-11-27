package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.jayway.jsonpath.JsonPath;

import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeadersAuthenticationFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.security.UnauthenticatedEntryPoint;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * A protected route without identity answers 401 with the ErrorResponse of the integration standards (4 and 5,
 * RNF-MS01-04): status, code NO_AUTENTICADO, message, errors and correlation_id
 */
class UnauthenticatedEntryPointTest {

    private UnauthenticatedEntryPoint entryPoint;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

        /**
     * Prepares the entry point, a request and an empty response
     */
    @BeforeEach
    void setUp() {
        entryPoint = new UnauthenticatedEntryPoint();
        request = new MockHttpServletRequest("GET", "/api/v1/roles");
        response = new MockHttpServletResponse();
    }

        /**
     * Runs the entry point
     *
     * @throws Exception if writing the response fails
     */
    private void commence() throws Exception {
        entryPoint.commence(request, response, new InsufficientAuthenticationException("without identity"));
    }

        /**
     * The response is 401 in JSON with the normalized body
     *
     * @throws Exception if writing the response fails
     */
    @Test
    void commence_returns401WithTheNormalizedBody() throws Exception {
        commence();

        String body = response.getContentAsString();
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals(401, JsonPath.<Integer>read(body, "$.status"));
        assertEquals("NO_AUTENTICADO", JsonPath.<String>read(body, "$.codigo"));
        assertEquals(List.of(), JsonPath.<List<Object>>read(body, "$.errores"));
        assertEquals(5, JsonPath.<java.util.Map<String, Object>>read(body, "$").size());
    }

        /**
     * Without a reason stored by the filter it is reported that the identity headers are missing
     *
     * @throws Exception if writing the response fails
     */
    @Test
    void commence_withoutStoredReason_reportsMissingHeaders() throws Exception {
        commence();

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE,
                JsonPath.<String>read(response.getContentAsString(), "$.mensaje"));
    }

        /**
     * With a reason stored by the filter that reason is reported
     *
     * @throws Exception if writing the response fails
     */
    @Test
    void commence_withStoredReason_reportsThatReason() throws Exception {
        request.setAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE,
                IdentityHeaderParser.INVALID_ROLE_MESSAGE);

        commence();

        assertEquals(IdentityHeaderParser.INVALID_ROLE_MESSAGE,
                JsonPath.<String>read(response.getContentAsString(), "$.mensaje"));
    }

        /**
     * The correlation id of the request is returned in the body
     *
     * @throws Exception if writing the response fails
     */
    @Test
    void commence_withCorrelationHeader_returnsItInTheBody() throws Exception {
        request.addHeader(CorrelationIds.HEADER, "c19b472d-7a2a-4d3b-a12b-abcdef123456");

        commence();

        assertEquals("c19b472d-7a2a-4d3b-a12b-abcdef123456",
                JsonPath.<String>read(response.getContentAsString(), "$.correlation_id"));
    }

        /**
     * Without a correlation id in the request the body carries a generated one, because the field is required
     *
     * @throws Exception if writing the response fails
     */
    @Test
    void commence_withoutCorrelationHeader_returnsAGeneratedOne() throws Exception {
        commence();

        String correlationId = JsonPath.<String>read(response.getContentAsString(), "$.correlation_id");
        assertDoesNotThrow(() -> UUID.fromString(correlationId));
    }
}
