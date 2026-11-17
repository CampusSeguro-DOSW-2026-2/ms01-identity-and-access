package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.PasswordController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PasswordService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * HTTP security of the password recovery and change with the real filter chain (RF-MS01-08, RN-MS01-23), the code
 * request and the reset are public because they are used without a session, the password change requires the identity
 * headers injected by the Gateway
 */
@WebMvcTest(PasswordController.class)
@Import(SecurityConfig.class)
class PasswordSecurityTest {

    private static final String FORGOT_URL = "/api/v1/auth/password/forgot";
    private static final String RESET_URL = "/api/v1/auth/password/reset";
    private static final String CHANGE_URL = "/api/v1/users/me/password";
    private static final String USER_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String FORGOT_BODY = "{\"correo\":\"maria.perez@escuelaing.edu.co\"}";
    private static final String RESET_BODY = "{\"correo\":\"maria.perez@escuelaing.edu.co\",\"codigo\":\"482913\","
            + "\"nueva_contrasena\":\"Nueva#2026x\",\"confirmar_nueva_contrasena\":\"Nueva#2026x\"}";
    private static final String CHANGE_BODY = "{\"contrasena_actual\":\"Campus#2026\","
            + "\"nueva_contrasena\":\"Nueva#2026x\",\"confirmar_nueva_contrasena\":\"Nueva#2026x\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordService passwordService;

        /**
     * The code request is public: it works without identity headers and without a token
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void forgot_withoutIdentityHeaders_isPublicAndReachesTheService() throws Exception {
        mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(FORGOT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje")
                        .value("Si el correo está registrado, enviamos un código de recuperación."));

        verify(passwordService).requestRecoveryCode(any(ForgotPasswordRequest.class), any(String.class));
    }

        /**
     * The reset with a code is public: it works without identity headers and without a token
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void reset_withoutIdentityHeaders_isPublicAndReachesTheService() throws Exception {
        mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(RESET_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        verify(passwordService).resetPassword(any(ResetPasswordRequest.class), any(String.class));
    }

        /**
     * The two public routes return the same correlation id that they received
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void publicRoutes_echoTheCorrelationId() throws Exception {
        String correlationId = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(FORGOT_BODY)
                .header(CorrelationIds.HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, correlationId));
        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RESET_BODY)
                .header(CorrelationIds.HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, correlationId));
    }

        /**
     * The password change is not public: without identity headers it answers 401 with the ErrorResponse and the service
     * is not run
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void change_withoutIdentityHeaders_returns401WithErrorResponse() throws Exception {
        mockMvc.perform(put(CHANGE_URL).contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());

        verifyNoInteractions(passwordService);
    }

        /**
     * A token in Authorization without the Gateway headers does not authenticate: MS01 neither receives nor validates
     * it
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void change_withOnlyABearerToken_returns401AndDoesNotReachTheService() throws Exception {
        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_BODY)
                .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.e30.firma"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(passwordService);
    }

        /**
     * A role name with an invalid format answers 401 with the reason and the service is not run
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void change_withInvalidRoleFormat_returns401WithTheReason() throws Exception {
        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_BODY)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "SUPER-USUARIO")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, "ms01:role:read_own"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.INVALID_ROLE_MESSAGE));

        verifyNoInteractions(passwordService);
    }

        /**
     * With valid headers the request reaches the service with the received identity and correlation id, and the
     * response returns the same correlation id
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void change_withIdentityHeaders_reachesTheServiceAndEchoesTheCorrelationId() throws Exception {
        String correlationId = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_BODY)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE"))
                .header(CorrelationIds.HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, correlationId))
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente."));

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(passwordService).changePassword(captor.capture(), any(ChangePasswordRequest.class),
                eq(correlationId));
        assertEquals(UUID.fromString(USER_ID), captor.getValue().userId());
        assertEquals(Set.of("ESTUDIANTE"), captor.getValue().roles());
    }

        /**
     * All the roles of the catalog can change their password: the route does not restrict by role
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void change_everyRoleInTheCatalog_isAccepted() throws Exception {
        for (String role : SeedRoles.PERMISSIONS.keySet()) {
            mockMvc.perform(put(CHANGE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CHANGE_BODY)
                    .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                    .header(IdentityHeaderParser.USER_ROLE_HEADER, role)
                    .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader(role)))
                    .andExpect(status().isOk());
        }
    }

        /**
     * The public routes are only public with their method: a GET to the code request is not public and answers 401
     * without identity
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void forgot_withAnotherHttpMethod_isNotPublic() throws Exception {
        mockMvc.perform(put(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(FORGOT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(passwordService);
    }
}
