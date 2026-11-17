package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.RoleAdminController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleAdminService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * HTTP security of the role administration routes with the real filter chain (RN-MS01-23): none is public, without
 * identity headers they answer 401 with the ErrorResponse and with headers they reach the controller, which decides by
 * permissions
 */
@WebMvcTest(RoleAdminController.class)
@Import(SecurityConfig.class)
class RoleAdminSecurityTest {

    private static final String ADMIN_ID = "a0000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleAdminService roleAdminService;

        /**
     * No role administration route is public: without headers it answers 401 and does not reach the service
     *
     * @param method HTTP method
     * @param path route
     * @throws Exception if the simulated request fails
     */
    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/roles/details",
            "GET,/api/v1/roles/MONITOR",
            "GET,/api/v1/permissions",
            "POST,/api/v1/roles",
            "PUT,/api/v1/roles/MONITOR",
            "PUT,/api/v1/roles/MONITOR/permissions",
            "DELETE,/api/v1/roles/MONITOR"
    })
    void roleAdminRoutes_withoutIdentityHeaders_return401(String method, String path) throws Exception {
        var request = switch (method) {
            case "POST" -> post(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "PUT" -> put(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "DELETE" -> delete(path);
            default -> get(path);
        };

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE))
                .andExpect(header().exists(CorrelationIds.HEADER));

        verifyNoInteractions(roleAdminService);
    }

        /**
     * Without X-User-Permission the request has no identity and answers 401, even if it carries the ADMIN role
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void roleAdminRoutes_withoutThePermissionHeader_return401() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE));

        verifyNoInteractions(roleAdminService);
    }

        /**
     * A permission code with an invalid format in X-User-Permission answers 401 with the reason
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void roleAdminRoutes_withAnInvalidPermissionFormat_return401() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, "MS01:ROLE:MANAGE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.INVALID_PERMISSION_MESSAGE));

        verifyNoInteractions(roleAdminService);
    }

        /**
     * With the three headers the request reaches the controller
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void roleAdminRoutes_withIdentityHeaders_reachTheController() throws Exception {
        when(roleAdminService.listPermissions(any())).thenReturn(List.of(
                new PermissionItemResponse("ms01:profile:read", "Consultar el propio perfil")));

        mockMvc.perform(get("/api/v1/permissions")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].permiso").value("ms01:profile:read"));

        verify(roleAdminService).listPermissions(any());
    }

        /**
     * The internal session cuts route is not served by this chain: without the internal chain it still requires a user
     * identity
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void sessionCutsPath_isNotPublicInTheDefaultChain() throws Exception {
        mockMvc.perform(get("/api/v1/internal/session-cuts"))
                .andExpect(status().isUnauthorized());
    }
}
