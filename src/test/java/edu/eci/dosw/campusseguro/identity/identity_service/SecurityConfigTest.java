package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.RoleController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * HTTP security with the real filter chain (integration standards 1.3 and 2.3, RN-MS01-23): only the exact routes of
 * the standard are public, the rest require the identity headers and answer 401 with the ErrorResponse, and the
 * X-Correlation-ID is always returned
 */
@WebMvcTest(RoleController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final String ADMIN_ID = "a0000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

        /**
     * A protected route without headers answers 401 with the normalized body
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void protectedRoute_withoutIdentityHeaders_returns401WithErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());

        verifyNoInteractions(roleService);
    }

        /**
     * The received correlation id is kept in the 401 response, both in the header and in the body
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void protectedRoute_withCorrelationId_returnsTheSameValueInHeaderAndBody() throws Exception {
        mockMvc.perform(get("/api/v1/roles").header(CorrelationIds.HEADER, "c19b472d-7a2a-4d3b-a12b-abcdef123456"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIds.HEADER, "c19b472d-7a2a-4d3b-a12b-abcdef123456"))
                .andExpect(jsonPath("$.correlation_id").value("c19b472d-7a2a-4d3b-a12b-abcdef123456"));
    }

        /**
     * Without a correlation id in the request one is generated and the header and the body carry the same value
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void protectedRoute_withoutCorrelationId_generatesOneForHeaderAndBody() throws Exception {
        String generated = mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(CorrelationIds.HEADER))
                .andReturn().getResponse().getHeader(CorrelationIds.HEADER);

        mockMvc.perform(get("/api/v1/roles").header(CorrelationIds.HEADER, generated))
                .andExpect(jsonPath("$.correlation_id").value(generated));
    }

        /**
     * A role name with an invalid format in X-User-Role answers 401 with the reason
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void protectedRoute_withInvalidRoleFormat_returns401WithTheReason() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "SUPER-USUARIO")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, "ms01:role:read_own"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.INVALID_ROLE_MESSAGE));
    }

        /**
     * With valid headers the request reaches the controller
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void protectedRoute_withIdentityHeaders_reachesTheController() throws Exception {
        when(roleService.getRoles(any())).thenReturn(List.of(
                new RolePermissionsResponse("ESTUDIANTE", List.of("ms01:profile:read"))));

        mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIds.HEADER))
                .andExpect(jsonPath("$[0].rol").value("ESTUDIANTE"));
    }

        /**
     * The role change without headers answers 401 and does not reach the service
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void changeRoles_withoutIdentityHeaders_returns401AndDoesNotReachTheService() throws Exception {
        mockMvc.perform(patch("/api/v1/users/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c\"],\"agregar\":[\"PROFESOR\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(roleService);
    }

        /**
     * The four public routes of the standard pass the security without headers, in this test their controller does not
     * exist, so the response is the normalized 404 and not a 401
     *
     * @param path public route of the standard
     * @throws Exception if the simulated request fails
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset" })
    void publicRoutes_doNotRequireIdentity(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

        /**
     * The other routes under /auth are not public, the standard forbids public wildcards
     *
     * @param path route that is not in the public list
     * @throws Exception if the simulated request fails
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/logout",
            "/api/v1/auth/otra",
            "/api/v1/auth/password/otra" })
    void otherAuthRoutes_areNotPublic(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

        /**
     * A public route is only public with its exact method, GET /auth/login requires identity
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void publicRoute_withAnotherMethod_isNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized());
    }

        /**
     * The user routes are no longer public
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void usersRoutes_areNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

        /**
     * With a valid identity a non-existent route answers the normalized 404
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void unknownRoute_withIdentity_returnsTheNormalized404() throws Exception {
        mockMvc.perform(get("/api/v1/inexistente")
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
     * With a valid identity an unsupported method answers the normalized 405
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void wrongMethod_withIdentity_returnsTheNormalized405() throws Exception {
        mockMvc.perform(get("/api/v1/users/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "PATCH"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

        /**
     * The health check is public for the Gateway and the orchestrators, in this test the endpoint does not exist, so
     * the response is the normalized 404 and not a 401
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

        /**
     * The other actuator endpoints are not public
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void otherActuatorEndpoints_areNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

        /**
     * Swagger is only public in local development, by default it requires identity
     *
     * @param path route of the interactive documentation
     * @throws Exception if the simulated request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs" })
    void swaggerRoutes_areNotPublicByDefault(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

        /**
     * CORS is not allowed in the microservice, it is handled by the Gateway
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void cors_isNotConfiguredInTheMicroservice() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                .header("Origin", "http://localhost:3000")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
