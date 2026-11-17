package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.ErrorResponseController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.RequestDispatcher;

/**
 * The errors thrown by the container, such as a non-existent route, also come out with the standard ErrorResponse
 */
class ErrorResponseControllerTest {

    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    private ErrorResponseController controller;
    private MockHttpServletRequest request;

        /**
     * Prepares the controller and an empty request
     */
    @BeforeEach
    void setUp() {
        controller = new ErrorResponseController();
        request = new MockHttpServletRequest();
    }

        /**
     * Without a status registered by the container it is reported that the resource does not exist
     */
    @Test
    void handleError_withoutStatus_isNotFound() {
        ResponseEntity<ErrorResponse> response = controller.handleError(request);

        assertEquals(404, response.getStatusCode().value());
        assertEquals("NO_ENCONTRADO", response.getBody().codigo());
        assertEquals("Recurso no encontrado.", response.getBody().mensaje());
    }

        /**
     * The status registered by the container decides the code and the message
     */
    @Test
    void handleError_withRegisteredStatus_usesTheCatalog() {
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 405);

        ResponseEntity<ErrorResponse> response = controller.handleError(request);

        assertEquals(405, response.getStatusCode().value());
        assertEquals(405, response.getBody().status());
        assertEquals("METODO_NO_PERMITIDO", response.getBody().codigo());
        assertEquals("Método HTTP no permitido.", response.getBody().mensaje());
    }

        /**
     * A status that does not exist is reported as an internal error
     */
    @Test
    void handleError_withUnknownStatus_isInternalError() {
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 799);

        ResponseEntity<ErrorResponse> response = controller.handleError(request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("ERROR_INTERNO", response.getBody().codigo());
    }

        /**
     * The correlation id of the request is kept in the body
     */
    @Test
    void handleError_keepsTheCorrelationId() {
        request.addHeader(CorrelationIds.HEADER, CORRELATION_ID);

        ResponseEntity<ErrorResponse> response = controller.handleError(request);

        assertEquals(CORRELATION_ID, response.getBody().correlationId());
        assertTrue(response.getBody().errores().isEmpty());
    }
}
