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

import java.util.EnumSet;
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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * Pruebas de POST /api/v1/auth/logout (RF-MS01-04), el endpoint recibe la identidad solo por las cabeceras
 * del Gateway, no recibe cuerpo y responde con el mensaje de confirmación del contrato
 */
@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    private static final String URL = "/api/v1/auth/logout";
    private static final String ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private SessionController sessionController;

    private MockMvc mockMvc;

    /**
     * Prepara el controlador y el cliente de pruebas con el manejador global de errores y la resolución de identidad
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(sessionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new RequesterIdentityArgumentResolver())
                .build();
    }

    /**
     * Un logout con identidad válida responde 200 con el mensaje de confirmación del contrato
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void logout_validIdentity_returns200WithTheConfirmationMessage() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(URL)
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada."));
    }

    /**
     * La respuesta solo lleva el mensaje: no devuelve tokens ni datos del usuario
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void logout_response_containsOnlyTheMessage() throws Exception {
        mockMvc.perform(post(URL)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.usuario").doesNotExist());
    }

    /**
     * El servicio recibe la identidad de las cabeceras, con todos sus roles, y el correlation id recibido
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void logout_validIdentity_passesTheIdentityAndTheCorrelationIdToTheService() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(URL)
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "BRIGADISTA,ESTUDIANTE")
                .header(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(status().isOk());

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(sessionService).logout(captor.capture(), eq(CORRELATION_ID));
        assertEquals(id, captor.getValue().userId());
        assertEquals(EnumSet.of(UserRole.BRIGADISTA, UserRole.ESTUDIANTE), captor.getValue().roles());
    }

    /**
     * Sin X-Correlation-ID el controlador entrega al servicio uno generado
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void logout_withoutCorrelationId_passesAGeneratedOneToTheService() throws Exception {
        mockMvc.perform(post(URL)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "PROFESOR"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sessionService).logout(any(RequesterIdentity.class), captor.capture());
        assertEquals(captor.getValue(), UUID.fromString(captor.getValue()).toString());
        assertNotEquals(CORRELATION_ID, captor.getValue());
    }

    /**
     * El cuerpo de la petición se ignora: el logout no tiene formulario y la identidad no viaja en el cuerpo
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void logout_withARequestBody_ignoresItAndUsesTheHeaderIdentity() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"roles\":[\"ADMIN\"]}")
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isOk());

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(sessionService).logout(captor.capture(), any(String.class));
        assertEquals(id, captor.getValue().userId());
        assertEquals(EnumSet.of(UserRole.ESTUDIANTE), captor.getValue().roles());
    }

    /**
     * Sin cabeceras de identidad la respuesta es 401 con el ErrorResponse y no se llama al servicio
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Una identidad incompleta o con valores inválidos responde 401 y no se llama al servicio
     *
     * @param userId valor de X-User-Id, vacío si no se envía
     * @param roles valor de X-User-Role, vacío si no se envía
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
            request = request.header(ROLE_HEADER, roles);
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(sessionService);
    }

    /**
     * El token por sí solo no autentica: MS01 nunca lo valida y solo confía en las cabeceras del Gateway
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void logout_withOnlyAnAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.e30.firma"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(sessionService);
    }

    /**
     * Si el servicio rechaza por falta de permiso la respuesta es 403 con el ErrorResponse
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void logout_serviceRejectsForbidden_returns403() throws Exception {
        doThrow(new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage()))
                .when(sessionService).logout(any(RequesterIdentity.class), any(String.class));

        mockMvc.perform(post(URL)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.mensaje").value(ErrorCode.FORBIDDEN.getMessage()));
    }

    /**
     * Un método HTTP distinto de POST responde 405 con el ErrorResponse y la cabecera Allow
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void logout_wrongHttpMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(get(URL)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));

        verifyNoInteractions(sessionService);
    }
}
