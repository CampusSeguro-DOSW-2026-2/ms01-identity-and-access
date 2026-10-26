package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.SessionController;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * Seguridad HTTP del cierre de sesión con la cadena de filtros real (RF-MS01-04, RN-MS01-23), la ruta no es
 * pública: exige las cabeceras de identidad que inyecta el Gateway y el token por sí solo no autentica
 */
@WebMvcTest(SessionController.class)
@Import(SecurityConfig.class)
class SessionSecurityTest {

    private static final String URL = "/api/v1/auth/logout";
    private static final String USER_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    /**
     * Sin cabeceras de identidad la ruta responde 401 con el ErrorResponse y el servicio no se ejecuta
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void logout_withoutIdentityHeaders_returns401WithErrorResponse() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());

        verifyNoInteractions(sessionService);
    }

    /**
     * Un token en Authorization sin las cabeceras del Gateway no autentica: MS01 no lo recibe ni lo valida
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void logout_withOnlyABearerToken_returns401AndDoesNotReachTheService() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.e30.firma"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(sessionService);
    }

    /**
     * Un rol fuera del catálogo responde 401 con el motivo y el servicio no se ejecuta
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void logout_withUnknownRole_returns401WithTheReason() throws Exception {
        mockMvc.perform(post(URL)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "SUPERUSUARIO"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.INVALID_ROLE_MESSAGE));

        verifyNoInteractions(sessionService);
    }

    /**
     * Con cabeceras válidas la petición llega al servicio con la identidad y el correlation id recibidos, y
     * la respuesta devuelve el mismo correlation id
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void logout_withIdentityHeaders_reachesTheServiceAndEchoesTheCorrelationId() throws Exception {
        String correlationId = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        mockMvc.perform(post(URL)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE")
                .header(CorrelationIds.HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, correlationId))
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada."));

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(sessionService).logout(captor.capture(), eq(correlationId));
        assertEquals(UUID.fromString(USER_ID), captor.getValue().userId());
        assertEquals(EnumSet.of(UserRole.ESTUDIANTE), captor.getValue().roles());
    }

    /**
     * Todos los roles del catálogo pueden cerrar sesión: la ruta no restringe por rol
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void logout_everyRoleInTheCatalog_isAccepted() throws Exception {
        for (UserRole role : UserRole.values()) {
            mockMvc.perform(post(URL)
                    .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                    .header(IdentityHeaderParser.USER_ROLE_HEADER, role.name()))
                    .andExpect(status().isOk());
        }

        verify(sessionService, times(UserRole.values().length))
                .logout(any(RequesterIdentity.class), any(String.class));
    }
}
