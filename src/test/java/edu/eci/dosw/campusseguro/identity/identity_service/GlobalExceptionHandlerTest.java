package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.RoleController;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * Normalized technical errors (RNF-MS01-04, integration standards 4 and 5): an uncontrolled failure answers 500
 * ERROR_INTERNO without exposing internal details, an unsupported content type answers 415 and the exceptions that
 * already carry their HTTP status keep that status
 */
@WebMvcTest(RoleController.class)
@Import(SecurityConfig.class)
class GlobalExceptionHandlerTest {

    private static final String ADMIN_ID = "a0000000-0000-4000-8000-000000000001";
    private static final String INTERNAL_DETAIL = "detalle interno que no debe salir";
    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

        /**
     * An uncontrolled failure answers 500 with the normalized body and without the text of the exception
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void unexpectedFailure_returns500WithoutLeakingTheException() throws Exception {
        when(roleService.getRoles(any())).thenThrow(new IllegalStateException(INTERNAL_DETAIL));

        MvcResult result = mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.mensaje").value("Error interno del servidor."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty())
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(INTERNAL_DETAIL));
    }

        /**
     * The received correlation id is returned in the header and in the body of the 500
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void unexpectedFailure_returnsTheCorrelationIdInHeaderAndBody() throws Exception {
        when(roleService.getRoles(any())).thenThrow(new IllegalStateException(INTERNAL_DETAIL));

        mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN"))
                .header(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));
    }

        /**
     * The detail of the failure stays in the log with the correlation id in the MDC and is not returned to the client
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void unexpectedFailure_isLoggedWithTheCorrelationIdInTheMdc() throws Exception {
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
        try {
            when(roleService.getRoles(any())).thenThrow(new IllegalStateException(INTERNAL_DETAIL));

            mockMvc.perform(get("/api/v1/roles")
                    .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                    .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                    .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN"))
                    .header(CorrelationIds.HEADER, CORRELATION_ID))
                    .andExpect(status().isInternalServerError());

            assertEquals(1, appender.list.size());
            ILoggingEvent logged = appender.list.get(0);
            assertEquals("Uncontrolled error", logged.getFormattedMessage());
            assertEquals(CORRELATION_ID, logged.getMDCPropertyMap().get(CorrelationIds.MDC_KEY));
            assertEquals(INTERNAL_DETAIL, logged.getThrowableProxy().getMessage());
        } finally {
            handlerLogger.detachAppender(appender);
        }
    }

        /**
     * An exception that already carries its HTTP status keeps it and is not converted into 500
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void responseStatusException_keepsItsOwnStatus() throws Exception {
        when(roleService.getRoles(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));

        mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isConflict());
    }

        /**
     * A body with an unsupported content type answers 415 with the normalized body
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void unsupportedMediaType_returns415WithTheNormalizedBody() throws Exception {
        mockMvc.perform(patch("/api/v1/users/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN"))
                .contentType(MediaType.TEXT_PLAIN)
                .content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"))
                .andExpect(jsonPath("$.mensaje").value("Tipo de contenido no soportado."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());

        verifyNoInteractions(roleService);
    }

        /**
     * A malformed JSON body answers 400 VALIDACION and does not reach the service
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void malformedJson_returns400AndDoesNotReachTheService() throws Exception {
        mockMvc.perform(patch("/api/v1/users/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(roleService);
    }

        /**
     * A route that does not exist answers 404 NO_ENCONTRADO with the normalized body
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void unknownRoute_returns404WithTheNormalizedBody() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());
    }

        /**
     * A not allowed HTTP method answers 405 with the Allow header and the normalized body
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void methodNotAllowed_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

        /**
     * A body with keys of a type that does not correspond answers 400 VALIDACION
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void bodyWithWrongTypes_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\": \"no-es-una-lista\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(roleService);
    }
}
