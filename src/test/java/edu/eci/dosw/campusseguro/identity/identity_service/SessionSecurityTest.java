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

import java.util.Set;
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
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.SessionService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * HTTP security of the logout with the real filter chain (RF-MS01-04, RN-MS01-23), the route is not public: it requires
 * the identity headers injected by the Gateway and the token alone does not authenticate
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
     * Without identity headers the route answers 401 with the ErrorResponse and the service is not run
     *
     * @throws Exception if the simulated request fails
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
     * A token in Authorization without the Gateway headers does not authenticate: MS01 neither receives nor validates
     * it
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void logout_withOnlyABearerToken_returns401AndDoesNotReachTheService() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.e30.firma"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(sessionService);
    }

        /**
     * A role name with an invalid format answers 401 with the reason and the service is not run
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void logout_withInvalidRoleFormat_returns401WithTheReason() throws Exception {
        mockMvc.perform(post(URL)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "SUPER-USUARIO")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, "ms01:role:read_own"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.INVALID_ROLE_MESSAGE));

        verifyNoInteractions(sessionService);
    }

        /**
     * With valid headers the request reaches the service with the received identity and correlation id, and the
     * response returns the same correlation id
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void logout_withIdentityHeaders_reachesTheServiceAndEchoesTheCorrelationId() throws Exception {
        String correlationId = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        mockMvc.perform(post(URL)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE"))
                .header(CorrelationIds.HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, correlationId))
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada."));

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(sessionService).logout(captor.capture(), eq(correlationId));
        assertEquals(UUID.fromString(USER_ID), captor.getValue().userId());
        assertEquals(Set.of("ESTUDIANTE"), captor.getValue().roles());
    }

        /**
     * All the roles of the catalog can log out: the route does not restrict by role
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void logout_everyRoleInTheCatalog_isAccepted() throws Exception {
        for (String role : SeedRoles.PERMISSIONS.keySet()) {
            mockMvc.perform(post(URL)
                    .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                    .header(IdentityHeaderParser.USER_ROLE_HEADER, role)
                    .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader(role)))
                    .andExpect(status().isOk());
        }

        verify(sessionService, times(SeedRoles.PERMISSIONS.size()))
                .logout(any(RequesterIdentity.class), any(String.class));
    }
}
