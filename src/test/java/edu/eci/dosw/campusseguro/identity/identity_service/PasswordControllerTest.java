package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.PasswordController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidResetCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PasswordService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * Tests of the three endpoints of RF-MS01-08: POST /api/v1/auth/password/forgot, POST /api/v1/auth/password/reset and
 * PUT /api/v1/users/me/password, the first two are public and the third receives the identity only through the Gateway
 * headers
 */
@ExtendWith(MockitoExtension.class)
class PasswordControllerTest {

    private static final String FORGOT_URL = "/api/v1/auth/password/forgot";
    private static final String RESET_URL = "/api/v1/auth/password/reset";
    private static final String CHANGE_URL = "/api/v1/users/me/password";
    private static final String ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String PERMISSION_HEADER = "X-User-Permission";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";
    private static final String FORGOT_BODY = "{\"correo\":\"maria.perez@escuelaing.edu.co\"}";
    private static final String RESET_BODY = "{\"correo\":\"maria.perez@escuelaing.edu.co\",\"codigo\":\"482913\","
            + "\"nueva_contrasena\":\"Nueva#2026x\",\"confirmar_nueva_contrasena\":\"Nueva#2026x\"}";
    private static final String CHANGE_BODY = "{\"contrasena_actual\":\"Campus#2026\","
            + "\"nueva_contrasena\":\"Nueva#2026x\",\"confirmar_nueva_contrasena\":\"Nueva#2026x\"}";

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private PasswordController passwordController;

    private MockMvc mockMvc;

        /**
     * Prepares the controller and the test client with the global error handler and the identity resolution
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(passwordController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new RequesterIdentityArgumentResolver())
                .build();
    }

        /**
     * Builds the password change request with the identity of a requester
     *
     * @param id identifier of the requester
     * @param role role of the requester
     * @param body JSON body of the request
     * @return the request ready to run
     */
    private MockHttpServletRequestBuilder changeRequest(UUID id, String role, String body) {
        return put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, role)
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader(role));
    }

        /**
     * A code request answers 200 with the single message of the contract and without identity in the headers
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void forgot_validBody_returns200WithTheSingleMessage() throws Exception {
        mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(FORGOT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje")
                        .value("Si el correo está registrado, enviamos un código de recuperación."))
                .andExpect(jsonPath("$.length()").value(1));
    }

        /**
     * The request delivers to the service the email of the body and the received correlation id
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void forgot_validBody_passesTheEmailAndTheCorrelationIdToTheService() throws Exception {
        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(FORGOT_BODY)
                .header(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(status().isOk());

        ArgumentCaptor<ForgotPasswordRequest> captor = ArgumentCaptor.forClass(ForgotPasswordRequest.class);
        verify(passwordService).requestRecoveryCode(captor.capture(), eq(CORRELATION_ID));
        assertEquals("maria.perez@escuelaing.edu.co", captor.getValue().getEmail());
    }

        /**
     * Without X-Correlation-ID the controller delivers a generated one to the service
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void forgot_withoutCorrelationId_passesAGeneratedOneToTheService() throws Exception {
        mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content(FORGOT_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(passwordService).requestRecoveryCode(any(ForgotPasswordRequest.class), captor.capture());
        assertEquals(captor.getValue(), UUID.fromString(captor.getValue()).toString());
        assertNotEquals(CORRELATION_ID, captor.getValue());
    }

        /**
     * A field that the contract does not define does not make the deserialization fail: it reaches the service
     * registered, which rejects it (RG-04)
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void forgot_unknownField_reachesTheServiceRegisteredAsUnknown() throws Exception {
        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"maria.perez@escuelaing.edu.co\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ForgotPasswordRequest> captor = ArgumentCaptor.forClass(ForgotPasswordRequest.class);
        verify(passwordService).requestRecoveryCode(captor.capture(), any(String.class));
        assertTrue(captor.getValue().unknownFieldNames().contains("rol"));
    }

        /**
     * If the service rejects the email the response is 400 with the detail by field
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void forgot_serviceRejectsTheEmail_returns400WithTheFieldError() throws Exception {
        doThrow(new InvalidRequestException("Datos inválidos.",
                List.of(new ErrorResponse.ErrorDetail("correo", "Ingrese un correo válido."))))
                .when(passwordService).requestRecoveryCode(any(ForgotPasswordRequest.class), any(String.class));

        mockMvc.perform(post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingrese un correo válido."))
                .andExpect(jsonPath("$.correlation_id").exists());
    }

        /**
     * A malformed JSON or one without a body answers 400 without calling the service
     *
     * @param content body sent, empty if it is not sent
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @ParameterizedTest
    @CsvSource(value = { "'{ \"correo\": '", "''" })
    void forgot_malformedOrMissingBody_returns400WithoutCallingTheService(String content) throws Exception {
        MockHttpServletRequestBuilder request = post(FORGOT_URL).contentType(MediaType.APPLICATION_JSON);
        if (!content.isEmpty()) {
            request = request.content(content);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(passwordService);
    }

        /**
     * A method other than POST answers 405 with the ErrorResponse and the Allow header
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void forgot_wrongHttpMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(get(FORGOT_URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));

        verifyNoInteractions(passwordService);
    }

        /**
     * A successful reset answers 200 with the confirmation message
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void reset_validBody_returns200WithTheConfirmationMessage() throws Exception {
        mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(RESET_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente. Inicia sesión de nuevo."))
                .andExpect(jsonPath("$.length()").value(1));
    }

        /**
     * The names of the contract are mapped to the request: correo, codigo, nueva_contrasena and
     * confirmar_nueva_contrasena
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void reset_validBody_mapsEveryContractFieldAndPassesTheCorrelationId() throws Exception {
        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RESET_BODY)
                .header(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(status().isOk());

        ArgumentCaptor<ResetPasswordRequest> captor = ArgumentCaptor.forClass(ResetPasswordRequest.class);
        verify(passwordService).resetPassword(captor.capture(), eq(CORRELATION_ID));
        assertEquals("maria.perez@escuelaing.edu.co", captor.getValue().getEmail());
        assertEquals("482913", captor.getValue().getCode());
        assertEquals("Nueva#2026x", captor.getValue().getNewPassword());
        assertEquals("Nueva#2026x", captor.getValue().getNewPasswordConfirmation());
        assertTrue(captor.getValue().unknownFieldNames().isEmpty());
    }

        /**
     * The password confirmation name of the registration is not the one of this form: it is registered as an unknown
     * field and not as a confirmation
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void reset_registrationConfirmationName_isRegisteredAsUnknown() throws Exception {
        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"maria.perez@escuelaing.edu.co\",\"codigo\":\"482913\","
                        + "\"nueva_contrasena\":\"Nueva#2026x\",\"confirmar_contrasena\":\"Nueva#2026x\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ResetPasswordRequest> captor = ArgumentCaptor.forClass(ResetPasswordRequest.class);
        verify(passwordService).resetPassword(captor.capture(), any(String.class));
        assertTrue(captor.getValue().unknownFieldNames().contains("confirmar_contrasena"));
        assertEquals(null, captor.getValue().getNewPasswordConfirmation());
    }

        /**
     * An incorrect, expired or used code answers 400 with CODIGO_INVALIDO, the generic message and without errors by
     * field
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void reset_invalidCode_returns400WithTheGenericCodeError() throws Exception {
        doThrow(new InvalidResetCodeException())
                .when(passwordService).resetPassword(any(ResetPasswordRequest.class), any(String.class));

        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(RESET_BODY)
                .header(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("Código incorrecto o vencido."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));
    }

        /**
     * If the service rejects the data the response is 400 with VALIDACION and the detail by field
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void reset_serviceRejectsTheData_returns400WithTheFieldErrors() throws Exception {
        doThrow(new InvalidRequestException("Datos inválidos.", List.of(
                new ErrorResponse.ErrorDetail("confirmar_nueva_contrasena", "Las contraseñas no coinciden."))))
                .when(passwordService).resetPassword(any(ResetPasswordRequest.class), any(String.class));

        mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(RESET_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("confirmar_nueva_contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Las contraseñas no coinciden."));
    }

        /**
     * A malformed JSON answers 400 without calling the service
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void reset_malformedJson_returns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content("{ \"codigo\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(passwordService);
    }

        /**
     * A method other than POST answers 405 with the ErrorResponse and the Allow header
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void reset_wrongHttpMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(put(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(RESET_BODY))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));

        verifyNoInteractions(passwordService);
    }

        /**
     * A successful change answers 200 with the confirmation message
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void change_validRequest_returns200WithTheConfirmationMessage() throws Exception {
        mockMvc.perform(changeRequest(UUID.randomUUID(), "ESTUDIANTE", CHANGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente."))
                .andExpect(jsonPath("$.length()").value(1));
    }

        /**
     * The service receives the identity from the headers with all its roles, the body with the names of the contract
     * and the received correlation id
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void change_validRequest_passesTheIdentityTheBodyAndTheCorrelationIdToTheService() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(changeRequest(id, "BRIGADISTA,ESTUDIANTE", CHANGE_BODY)
                .header(CorrelationIds.HEADER, CORRELATION_ID))
                .andExpect(status().isOk());

        ArgumentCaptor<RequesterIdentity> identity = ArgumentCaptor.forClass(RequesterIdentity.class);
        ArgumentCaptor<ChangePasswordRequest> body = ArgumentCaptor.forClass(ChangePasswordRequest.class);
        verify(passwordService).changePassword(identity.capture(), body.capture(), eq(CORRELATION_ID));
        assertEquals(id, identity.getValue().userId());
        assertEquals(Set.of("BRIGADISTA", "ESTUDIANTE"), identity.getValue().roles());
        assertEquals("Campus#2026", body.getValue().getCurrentPassword());
        assertEquals("Nueva#2026x", body.getValue().getNewPassword());
        assertEquals("Nueva#2026x", body.getValue().getNewPasswordConfirmation());
    }

        /**
     * A user id in the body does not change whose password is changed: it is registered as an unknown field and the
     * identity remains the one from the headers
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void change_userIdInTheBody_isRegisteredAsUnknownAndTheHeaderIdentityIsUsed() throws Exception {
        UUID id = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        mockMvc.perform(changeRequest(id, "ESTUDIANTE", "{\"id\":\"" + other + "\","
                + "\"contrasena_actual\":\"Campus#2026\",\"nueva_contrasena\":\"Nueva#2026x\","
                + "\"confirmar_nueva_contrasena\":\"Nueva#2026x\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<RequesterIdentity> identity = ArgumentCaptor.forClass(RequesterIdentity.class);
        ArgumentCaptor<ChangePasswordRequest> body = ArgumentCaptor.forClass(ChangePasswordRequest.class);
        verify(passwordService).changePassword(identity.capture(), body.capture(), any(String.class));
        assertEquals(id, identity.getValue().userId());
        assertTrue(body.getValue().unknownFieldNames().contains("id"));
    }

        /**
     * Without identity headers the response is 401 with the ErrorResponse and the service is not called
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void change_withoutIdentityHeaders_returns401AndDoesNotCallTheService() throws Exception {
        mockMvc.perform(put(CHANGE_URL).contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.errores").isArray())
                .andExpect(jsonPath("$.correlation_id").exists());

        verifyNoInteractions(passwordService);
    }

        /**
     * An incomplete identity or one with invalid values answers 401 and the service is not called
     *
     * @param userId value of X-User-Id, empty if it is not sent
     * @param roles value of X-User-Role, empty if it is not sent
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @ParameterizedTest
    @CsvSource(value = {
            "'',ESTUDIANTE",
            "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42,''",
            "no-es-un-uuid,ESTUDIANTE",
            "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42,SUPERUSUARIO" })
    void change_withIncompleteOrInvalidIdentity_returns401(String userId, String roles) throws Exception {
        MockHttpServletRequestBuilder request = put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_BODY);
        if (!userId.isEmpty()) {
            request = request.header(ID_HEADER, userId);
        }
        if (!roles.isEmpty()) {
            request = request.header(ROLE_HEADER, roles)
            .header(PERMISSION_HEADER, TestIdentities.permissionHeader(roles));
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(passwordService);
    }

        /**
     * If the service rejects for lack of permission the response is 403 with the ErrorResponse
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void change_serviceRejectsForbidden_returns403() throws Exception {
        doThrow(new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage()))
                .when(passwordService)
                .changePassword(any(RequesterIdentity.class), any(ChangePasswordRequest.class), any(String.class));

        mockMvc.perform(changeRequest(UUID.randomUUID(), "ESTUDIANTE", CHANGE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

        /**
     * If the service rejects the data, for example the incorrect current password, the response is 400 with the detail
     * by field
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void change_serviceRejectsTheData_returns400WithTheFieldError() throws Exception {
        doThrow(new InvalidRequestException("Datos inválidos.", List.of(
                new ErrorResponse.ErrorDetail("contrasena_actual", "La contraseña actual es incorrecta."))))
                .when(passwordService)
                .changePassword(any(RequesterIdentity.class), any(ChangePasswordRequest.class), any(String.class));

        mockMvc.perform(changeRequest(UUID.randomUUID(), "ESTUDIANTE", CHANGE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena_actual"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La contraseña actual es incorrecta."));
    }

        /**
     * If the user of the token no longer exists the response is 404
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void change_userNoLongerExists_returns404() throws Exception {
        doThrow(new UserNotFoundException(UUID.randomUUID()))
                .when(passwordService)
                .changePassword(any(RequesterIdentity.class), any(ChangePasswordRequest.class), any(String.class));

        mockMvc.perform(changeRequest(UUID.randomUUID(), "ESTUDIANTE", CHANGE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

        /**
     * A malformed JSON or one without a body answers 400 without calling the service
     *
     * @param content body sent, empty if it is not sent
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @ParameterizedTest
    @CsvSource(value = { "'{ \"contrasena_actual\": '", "''" })
    void change_malformedOrMissingBody_returns400WithoutCallingTheService(String content) throws Exception {
        MockHttpServletRequestBuilder request = put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE")
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE"));
        if (!content.isEmpty()) {
            request = request.content(content);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(passwordService);
    }

        /**
     * A method other than PUT answers 405 with the ErrorResponse and the Allow header
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void change_wrongHttpMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(post(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_BODY)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE")
                .header(PERMISSION_HEADER, TestIdentities.permissionHeader("ESTUDIANTE")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "PUT"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));

        verifyNoInteractions(passwordService);
    }
}
