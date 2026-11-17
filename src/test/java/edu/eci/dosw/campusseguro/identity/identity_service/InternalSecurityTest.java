package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.identity.identity_service.config.InternalSecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.UserController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.InternalServiceAuthenticationFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

/**
 * HTTP security of the internal routes with the two real chains (RF-MS01-09): they are authorized by X-Service-Name and
 * do not change the security of the protected routes
 */
@WebMvcTest(UserController.class)
@Import({InternalSecurityConfig.class, SecurityConfig.class})
class InternalSecurityTest {

    private static final String SERVICE_HEADER = InternalServiceAuthenticationFilter.SERVICE_NAME_HEADER;
    private static final String BATCH_URL = "/api/v1/users/batch";
    private static final String BY_ROLE_URL = "/api/v1/users/by-role";
    private static final UUID USER_ID = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID MISSING_ID = UUID.fromString("9b2e7c41-0d3a-4f6b-8e15-6a4c2d9f0b73");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

        /**
     * Builds the sample profile without second name or second lastname
     *
     * @return the sample profile
     */
    private static UserProfileResponse profile() {
        return new UserProfileResponse(USER_ID, "María", null, "Pérez", null, "maria.perez@escuelaing.edu.co",
                List.of("BRIGADISTA", "ESTUDIANTE"), UserState.ACTIVO);
    }

        /**
     * Batch body with one id
     *
     * @return the JSON of the body
     */
    private static String batchBody() {
        return "{\"ids\":[\"" + USER_ID + "\"]}";
    }

        /**
     * An authorized service receives the batch with the field names of the contract
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void batch_fromAllowedService_returns200WithTheContractFields() throws Exception {
        when(userService.findUsersByIds(any(BatchUsersRequest.class)))
                .thenReturn(new BatchUsersResponse(List.of(profile()), List.of(MISSING_ID)));

        mockMvc.perform(post(BATCH_URL).header(SERVICE_HEADER, "ms03")
                .contentType(MediaType.APPLICATION_JSON).content(batchBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.usuarios[0].segundo_nombre").doesNotExist())
                .andExpect(jsonPath("$.usuarios[0].roles[0]").value("BRIGADISTA"))
                .andExpect(jsonPath("$.no_encontrados[0]").value(MISSING_ID.toString()));
    }

    /**
     * MS04 is admitted by default: it asks MS01 for the names of its users with the batch route
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void batch_fromMs04_returns200() throws Exception {
        when(userService.findUsersByIds(any(BatchUsersRequest.class)))
                .thenReturn(new BatchUsersResponse(List.of(profile()), List.of()));

        mockMvc.perform(post(BATCH_URL).header(SERVICE_HEADER, "ms04")
                .contentType(MediaType.APPLICATION_JSON).content(batchBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios[0].id").value(USER_ID.toString()));
    }

    /**
     * MS04 is admitted by default: it asks MS01 for the target population of an event with the by-role route
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void byRole_fromMs04_returns200() throws Exception {
        when(userService.searchByRole(eq("ESTUDIANTE"), isNull(), isNull(), isNull()))
                .thenReturn(new PageResponse<>(List.of(profile()), 0, 20, 1, 1));

        mockMvc.perform(get(BY_ROLE_URL).header(SERVICE_HEADER, "ms04").param("rol", "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(USER_ID.toString()));
    }

        /**
     * Without X-Service-Name the batch answers 401 with the reason and does not reach the service
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void batch_withoutServiceName_returns401WithTheReason() throws Exception {
        mockMvc.perform(post(BATCH_URL).contentType(MediaType.APPLICATION_JSON).content(batchBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(InternalServiceAuthenticationFilter.MISSING_MESSAGE))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());

        verifyNoInteractions(userService);
    }

        /**
     * The user headers do not replace X-Service-Name on the internal routes
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void batch_withUserHeadersOnly_returns401() throws Exception {
        mockMvc.perform(post(BATCH_URL)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID.toString())
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(batchBody()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

        /**
     * An unauthorized service receives 403
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void batch_fromServiceNotAllowed_returns403() throws Exception {
        mockMvc.perform(post(BATCH_URL).header(SERVICE_HEADER, "ms06")
                .contentType(MediaType.APPLICATION_JSON).content(batchBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(userService);
    }

        /**
     * The query by role reaches its method and not /{id}
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void byRole_isResolvedAsALiteralPathAndNotAsAnId() throws Exception {
        when(userService.searchByRole(eq("BRIGADISTA"), eq("2026-11-07T15:04:05Z"), eq("0"), eq("100")))
                .thenReturn(new PageResponse<>(List.of(profile()), 0, 100, 1, 1));

        mockMvc.perform(get(BY_ROLE_URL).header(SERVICE_HEADER, "ms05")
                .param("rol", "BRIGADISTA").param("creado_antes", "2026-11-07T15:04:05Z")
                .param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.total_paginas").value(1));

        verify(userService).searchByRole(eq("BRIGADISTA"), eq("2026-11-07T15:04:05Z"), eq("0"), eq("100"));
    }

        /**
     * Without optional parameters the query by role sends them as null to the service
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void byRole_withoutOptionalParameters_passesNull() throws Exception {
        when(userService.searchByRole(eq("ESTUDIANTE"), isNull(), isNull(), isNull()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(BY_ROLE_URL).header(SERVICE_HEADER, "ms03").param("rol", "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty());
    }

        /**
     * Without X-Service-Name the query by role answers 401
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void byRole_withoutServiceName_returns401() throws Exception {
        mockMvc.perform(get(BY_ROLE_URL).param("rol", "BRIGADISTA"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(InternalServiceAuthenticationFilter.MISSING_MESSAGE));

        verifyNoInteractions(userService);
    }

        /**
     * The protected routes do not change: GET /users/{id} still requires the user headers even if X-Service-Name
     * arrives
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void protectedUserById_withServiceNameOnly_stillReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + USER_ID).header(SERVICE_HEADER, "ms03"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE));

        verifyNoInteractions(userService);
    }

        /**
     * The protected routes do not change: GET /users/{id} with user headers reaches the controller
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void protectedUserById_withUserHeaders_reachesTheController() throws Exception {
        when(userService.getUserById(any(), eq(USER_ID))).thenReturn(profile());

        mockMvc.perform(get("/api/v1/users/" + USER_ID)
                .header(IdentityHeaderParser.USER_ID_HEADER, UUID.randomUUID().toString())
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }
}
