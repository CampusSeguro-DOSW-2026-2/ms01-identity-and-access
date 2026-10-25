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
 * Una ruta protegida sin identidad responde 401 con el ErrorResponse de los estandares de integracion (4 y
 * 5, RNF-MS01-04): status, codigo NO_AUTENTICADO, mensaje, errores y correlation_id
 */
class UnauthenticatedEntryPointTest {

    private UnauthenticatedEntryPoint entryPoint;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    /**
     * Prepara el punto de entrada, una peticion y una respuesta vacias
     */
    @BeforeEach
    void setUp() {
        entryPoint = new UnauthenticatedEntryPoint();
        request = new MockHttpServletRequest("GET", "/api/v1/roles");
        response = new MockHttpServletResponse();
    }

    /**
     * Ejecuta el punto de entrada
     *
     * @throws Exception si falla la escritura de la respuesta
     */
    private void commence() throws Exception {
        entryPoint.commence(request, response, new InsufficientAuthenticationException("sin identidad"));
    }

    /**
     * La respuesta es 401 en JSON con el cuerpo normalizado
     *
     * @throws Exception si falla la escritura de la respuesta
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
    }

    /**
     * Sin motivo guardado por el filtro se informa que faltan las cabeceras de identidad
     *
     * @throws Exception si falla la escritura de la respuesta
     */
    @Test
    void commence_withoutStoredReason_reportsMissingHeaders() throws Exception {
        commence();

        assertEquals(IdentityHeaderParser.MISSING_MESSAGE,
                JsonPath.<String>read(response.getContentAsString(), "$.mensaje"));
    }

    /**
     * Con un motivo guardado por el filtro se informa ese motivo
     *
     * @throws Exception si falla la escritura de la respuesta
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
     * El correlation id de la peticion se devuelve en el cuerpo
     *
     * @throws Exception si falla la escritura de la respuesta
     */
    @Test
    void commence_withCorrelationHeader_returnsItInTheBody() throws Exception {
        request.addHeader(CorrelationIds.HEADER, "c19b-472d-a12b");

        commence();

        assertEquals("c19b-472d-a12b",
                JsonPath.<String>read(response.getContentAsString(), "$.correlation_id"));
    }

    /**
     * Sin correlation id en la peticion el cuerpo lleva uno generado, porque el campo es obligatorio
     *
     * @throws Exception si falla la escritura de la respuesta
     */
    @Test
    void commence_withoutCorrelationHeader_returnsAGeneratedOne() throws Exception {
        commence();

        String correlationId = JsonPath.<String>read(response.getContentAsString(), "$.correlation_id");
        assertDoesNotThrow(() -> UUID.fromString(correlationId));
    }
}
