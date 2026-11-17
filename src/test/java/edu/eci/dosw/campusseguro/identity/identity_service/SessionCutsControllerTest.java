package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.identity.identity_service.config.InternalSecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.SessionCutsController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.SessionCutsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.InternalServiceAuthenticationFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionCutService;

/**
 * HTTP security and contract of GET /api/v1/internal/session-cuts with the real chains (RF-MS01-11): only the gateway
 * service is served, without user headers
 */
@WebMvcTest(SessionCutsController.class)
@Import({InternalSecurityConfig.class, SecurityConfig.class})
class SessionCutsControllerTest {

    private static final String SERVICE_HEADER = InternalServiceAuthenticationFilter.SERVICE_NAME_HEADER;
    private static final String URL = "/api/v1/internal/session-cuts";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionCutService sessionCutService;

        /**
     * Sample snapshot
     *
     * @return the response with a user cut and a role cut
     */
    private SessionCutsResponse snapshot() {
        return new SessionCutsResponse("2026-11-11T12:00:00Z", 3700,
                List.of(new SessionCutsResponse.UserCut("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c",
                        "2026-11-11T11:59:59Z")),
                List.of(new SessionCutsResponse.RoleCut("PROFESOR", "2026-11-11T11:45:10Z")));
    }

        /**
     * The gateway receives the snapshot with the field names of the contract
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void sessionCuts_fromGateway_returns200WithTheContractFields() throws Exception {
        when(sessionCutService.currentCuts()).thenReturn(snapshot());

        mockMvc.perform(get(URL).header(SERVICE_HEADER, "gateway"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generado_en").value("2026-11-11T12:00:00Z"))
                .andExpect(jsonPath("$.ventana_segundos").value(3700))
                .andExpect(jsonPath("$.usuarios[0].id").value("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c"))
                .andExpect(jsonPath("$.usuarios[0].valido_desde").value("2026-11-11T11:59:59Z"))
                .andExpect(jsonPath("$.roles[0].rol").value("PROFESOR"))
                .andExpect(jsonPath("$.roles[0].valido_desde").value("2026-11-11T11:45:10Z"));
    }

        /**
     * The service name is compared regardless of case and spaces
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void sessionCuts_serviceNameIsCaseAndSpaceInsensitive() throws Exception {
        when(sessionCutService.currentCuts()).thenReturn(snapshot());

        mockMvc.perform(get(URL).header(SERVICE_HEADER, " Gateway "))
                .andExpect(status().isOk());
    }

        /**
     * Without X-Service-Name it answers 401 with the reason and nothing is queried
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void sessionCuts_withoutServiceName_returns401WithTheReason() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(InternalServiceAuthenticationFilter.MISSING_MESSAGE))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());

        verifyNoInteractions(sessionCutService);
    }

        /**
     * The user headers, even those of an ADMIN, do not replace X-Service-Name
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void sessionCuts_withAdminUserHeadersOnly_returns401() throws Exception {
        mockMvc.perform(get(URL)
                .header(IdentityHeaderParser.USER_ID_HEADER, "a0000000-0000-4000-8000-000000000001")
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(sessionCutService);
    }

        /**
     * The services in the list of authorized ones of the internal routes, ms03, ms04 and ms05, cannot query the cuts:
     * only the gateway
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void sessionCuts_fromAnyOtherService_returns403() throws Exception {
        for (String service : List.of("ms03", "ms05", "ms04", "api-gateway", "gateway2")) {
            mockMvc.perform(get(URL).header(SERVICE_HEADER, service))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        }

        verifyNoInteractions(sessionCutService);
    }

        /**
     * The route only answers to GET, another method answers 405 with the normalized body
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void sessionCuts_otherMethods_areNotServed() throws Exception {
        mockMvc.perform(post(URL).header(SERVICE_HEADER, "gateway"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));

        verifyNoInteractions(sessionCutService);
    }
}
