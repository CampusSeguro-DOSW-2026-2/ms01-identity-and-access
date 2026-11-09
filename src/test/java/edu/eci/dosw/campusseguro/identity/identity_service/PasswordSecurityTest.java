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

import java.util.EnumSet;
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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PasswordService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * Seguridad HTTP de la recuperación y el cambio de contraseña con la cadena de filtros real (RF-MS01-08,
 * RN-MS01-23), la solicitud del código y el restablecimiento son públicos porque se usan sin sesión, el cambio
 * de contraseña exige las cabeceras de identidad que inyecta el Gateway
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
     * La solicitud del código es pública: funciona sin cabeceras de identidad y sin token
     *
     * @throws Exception si falla la petición simulada
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
     * El restablecimiento con código es público: funciona sin cabeceras de identidad y sin token
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void reset_withoutIdentityHeaders_isPublicAndReachesTheService() throws Exception {
        mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(RESET_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        verify(passwordService).resetPassword(any(ResetPasswordRequest.class), any(String.class));
    }

    /**
     * Las dos rutas públicas devuelven el mismo correlation id que recibieron
     *
     * @throws Exception si falla la petición simulada
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
     * El cambio de contraseña no es público: sin cabeceras de identidad responde 401 con el ErrorResponse y el
     * servicio no se ejecuta
     *
     * @throws Exception si falla la petición simulada
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
     * Un token en Authorization sin las cabeceras del Gateway no autentica: MS01 no lo recibe ni lo valida
     *
     * @throws Exception si falla la petición simulada
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
     * Un rol fuera del catálogo responde 401 con el motivo y el servicio no se ejecuta
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void change_withUnknownRole_returns401WithTheReason() throws Exception {
        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_BODY)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "SUPERUSUARIO"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.INVALID_ROLE_MESSAGE));

        verifyNoInteractions(passwordService);
    }

    /**
     * Con cabeceras válidas la petición llega al servicio con la identidad y el correlation id recibidos, y la
     * respuesta devuelve el mismo correlation id
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void change_withIdentityHeaders_reachesTheServiceAndEchoesTheCorrelationId() throws Exception {
        String correlationId = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_BODY)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE")
                .header(CorrelationIds.HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, correlationId))
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente."));

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(passwordService).changePassword(captor.capture(), any(ChangePasswordRequest.class),
                eq(correlationId));
        assertEquals(UUID.fromString(USER_ID), captor.getValue().userId());
        assertEquals(EnumSet.of(UserRole.ESTUDIANTE), captor.getValue().roles());
    }

    /**
     * Todos los roles del catálogo pueden cambiar su contraseña: la ruta no restringe por rol
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void change_everyRoleInTheCatalog_isAccepted() throws Exception {
        for (UserRole role : UserRole.values()) {
            mockMvc.perform(put(CHANGE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CHANGE_BODY)
                    .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID)
                    .header(IdentityHeaderParser.USER_ROLE_HEADER, role.name()))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Las rutas públicas solo lo son con su método: un GET a la solicitud del código no es público y responde
     * 401 sin identidad
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void forgot_withAnotherHttpMethod_isNotPublic() throws Exception {
        mockMvc.perform(put(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(FORGOT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(passwordService);
    }
}
