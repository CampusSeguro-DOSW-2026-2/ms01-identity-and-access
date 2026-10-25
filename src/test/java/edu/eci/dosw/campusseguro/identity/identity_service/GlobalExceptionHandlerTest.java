package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
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
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String ADMIN_ID = "a0000000-0000-4000-8000-000000000001";
    private static final String INTERNAL_DETAIL = "detalle interno que no debe salir";

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
                .header(CorrelationIds.HEADER, "c19b-472d-a12b"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(CorrelationIds.HEADER, "c19b-472d-a12b"))
                .andExpect(jsonPath("$.correlation_id").value("c19b-472d-a12b"));
    }

    /**
     * El detalle de la falla queda en el log, que empieza con el correlation id y no se devuelve al cliente
     *
     * @param output salida capturada de la consola
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void unexpectedFailure_isLoggedWithTheCorrelationId(CapturedOutput output) throws Exception {
        when(roleService.getRoles(any())).thenThrow(new IllegalStateException(INTERNAL_DETAIL));

        mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(CorrelationIds.HEADER, "c19b-472d-a12b"))
                .andExpect(status().isInternalServerError());

        assertTrue(output.getAll().contains("[c19b-472d-a12b] Error no controlado"));
        assertTrue(output.getAll().contains(INTERNAL_DETAIL));
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
                .andExpect(jsonPath("$.codigo").value("MEDIO_NO_SOPORTADO"))
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
}
