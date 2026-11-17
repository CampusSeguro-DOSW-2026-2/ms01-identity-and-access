package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.SessionController;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * Tests of POST /api/v1/auth/logout (RF-MS01-04), the endpoint receives the identity only through the Gateway headers,
 * receives no body and answers with the confirmation message of the contract
 */
@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    private static final String URL = "/api/v1/auth/logout";
    private static final String ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String PERMISSION_HEADER = "X-User-Permission";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private SessionController sessionController;

    private MockMvc mockMvc;

        /**
     * Prepares the controller and the test client with the global error handler and the identity resolution
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(sessionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new RequesterIdentityArgumentResolver())
                .build();
    }

        /**
     * A logout with a valid identity answers 200 with the confirmation message of the contract
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void logout_validIdentity_returns200WithTheConfirmationMessage() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(URL)
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "ESTUDIANTE")
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada."));
    }

        /**
     * The response only carries the message: it returns neither tokens nor user data
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void logout_response_containsOnlyTheMessage() throws Exception {
        mockMvc.perform(post(URL)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ADMIN")
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.usuario").doesNotExist());
    }

        /**
     * The service receives the identity from the headers, with all its roles, and the received correlation id
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void logout_validIdentity_passesTheIdentityAndTheCorrelationIdToTheService() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(URL)
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "BRIGADISTA,ESTUDIANTE")
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader("BRIGADISTA,ESTUDIANTE"))
                .header(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(status().isOk());

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(sessionService).logout(captor.capture(), eq(CORRELATION_ID));
        assertEquals(id, captor.getValue().userId());
        assertEquals(Set.of("BRIGADISTA", "ESTUDIANTE"), captor.getValue().roles());
    }

        /**
     * Without X-Correlation-ID the controller delivers a generated one to the service
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void logout_withoutCorrelationId_passesAGeneratedOneToTheService() throws Exception {
        mockMvc.perform(post(URL)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "PROFESOR")
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader("PROFESOR")))
                .andExpect(status().isOk());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sessionService).logout(any(RequesterIdentity.class), captor.capture());
        assertEquals(captor.getValue(), UUID.fromString(captor.getValue()).toString());
        assertNotEquals(CORRELATION_ID, captor.getValue());
    }

        /**
     * The body of the request is ignored: the logout has no form and the identity does not travel in the body
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void logout_withARequestBody_ignoresItAndUsesTheHeaderIdentity() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"roles\":[\"ADMIN\"]}")
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "ESTUDIANTE")
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE")))
                .andExpect(status().isOk());

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(sessionService).logout(captor.capture(), any(String.class));
        assertEquals(id, captor.getValue().userId());
        assertEquals(Set.of("ESTUDIANTE"), captor.getValue().roles());
    }

        /**
     * Without identity headers the response is 401 with the ErrorResponse and the service is not called
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void logout_withoutIdentityHeaders_returns401AndDoesNotCallTheService() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.errores").isArray())
                .andExpect(jsonPath("$.correlation_id").exists());

        verifyNoInteractions(sessionService);
    }

        /**
     * An incomplete identity or one with invalid values answers 401 and the service is not called
     *
     * @param userId value of X-User-Id, empty if it is not sent
     * @param roles value of X-User-Role, empty if it is not sent
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @ParameterizedTest
    @CsvSource(value = {
            "'',ESTUDIANTE",
            "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42,''",
            "no-es-un-uuid,ESTUDIANTE",
            "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42,SUPERUSUARIO",
            "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42,estudiante" })
    void logout_withIncompleteOrInvalidIdentity_returns401(String userId, String roles) throws Exception {
        MockHttpServletRequestBuilder request = post(URL);
        if (!userId.isEmpty()) {
            request = request.header(ID_HEADER, userId);
        }
        if (!roles.isEmpty()) {
            request = request.header(ROLE_HEADER, roles)
            .header(PERMISSION_HEADER, TestIdentities.permissionHeader(roles));
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(sessionService);
    }

        /**
     * The token alone does not authenticate: MS01 never validates it and only trusts the Gateway headers
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void logout_withOnlyAnAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.e30.firma"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(sessionService);
    }

        /**
     * If the service rejects for lack of permission the response is 403 with the ErrorResponse
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void logout_serviceRejectsForbidden_returns403() throws Exception {
        doThrow(new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage()))
                .when(sessionService).logout(any(RequesterIdentity.class), any(String.class));

        mockMvc.perform(post(URL)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE")
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.mensaje").value(ErrorCode.FORBIDDEN.getMessage()));
    }

        /**
     * An HTTP method other than POST answers 405 with the ErrorResponse and the Allow header
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void logout_wrongHttpMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(get(URL)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE")
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));

        verifyNoInteractions(sessionService);
    }
}
