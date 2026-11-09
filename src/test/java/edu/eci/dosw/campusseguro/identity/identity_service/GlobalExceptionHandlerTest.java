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
 * Errores tecnicos normalizados (RNF-MS01-04, estandares de integracion 4 y 5): una falla no controlada
 * responde 500 ERROR_INTERNO sin exponer detalles internos, un tipo de contenido no admitido responde 415 y
 * las excepciones que ya traen su estado HTTP conservan ese estado
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
     * Una falla no controlada responde 500 con el cuerpo normalizado y sin el texto de la excepcion
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void unexpectedFailure_returns500WithoutLeakingTheException() throws Exception {
        when(roleService.getRoles(any())).thenThrow(new IllegalStateException(INTERNAL_DETAIL));

        MvcResult result = mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN"))
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
     * El correlation id recibido se devuelve en el header y en el cuerpo del 500
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void unexpectedFailure_returnsTheCorrelationIdInHeaderAndBody() throws Exception {
        when(roleService.getRoles(any())).thenThrow(new IllegalStateException(INTERNAL_DETAIL));

        mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));
    }

    /**
     * El detalle de la falla queda en el log con el correlation id en el MDC y no se devuelve al cliente
     *
     * @throws Exception si falla la peticion simulada
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
                    .header(CorrelationIds.HEADER, CORRELATION_ID))
                    .andExpect(status().isInternalServerError());

            assertEquals(1, appender.list.size());
            ILoggingEvent logged = appender.list.get(0);
            assertEquals("Error no controlado", logged.getFormattedMessage());
            assertEquals(CORRELATION_ID, logged.getMDCPropertyMap().get(CorrelationIds.MDC_KEY));
            assertEquals(INTERNAL_DETAIL, logged.getThrowableProxy().getMessage());
        } finally {
            handlerLogger.detachAppender(appender);
        }
    }

    /**
     * Una excepcion que ya trae su estado HTTP lo conserva y no se convierte en 500
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void responseStatusException_keepsItsOwnStatus() throws Exception {
        when(roleService.getRoles(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));

        mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isConflict());
    }

    /**
     * Un cuerpo con tipo de contenido no admitido responde 415 con el cuerpo normalizado
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void unsupportedMediaType_returns415WithTheNormalizedBody() throws Exception {
        mockMvc.perform(patch("/api/v1/users/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
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
     * Un cuerpo JSON mal formado responde 400 VALIDACION y no llega al servicio
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void malformedJson_returns400AndDoesNotReachTheService() throws Exception {
        mockMvc.perform(patch("/api/v1/users/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(roleService);
    }

    /**
     * Una ruta que no existe responde 404 NO_ENCONTRADO con el cuerpo normalizado
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void unknownRoute_returns404WithTheNormalizedBody() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());
    }

    /**
     * Un método HTTP no permitido responde 405 con la cabecera Allow y el cuerpo normalizado
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void methodNotAllowed_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    /**
     * Un cuerpo con las claves en un tipo que no corresponde responde 400 VALIDACION
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void bodyWithWrongTypes_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\": \"no-es-una-lista\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(roleService);
    }
}
