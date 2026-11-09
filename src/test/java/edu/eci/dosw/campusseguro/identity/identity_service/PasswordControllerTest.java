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

import java.util.EnumSet;
import java.util.List;
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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
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
 * Pruebas de los tres endpoints de RF-MS01-08: POST /api/v1/auth/password/forgot, POST
 * /api/v1/auth/password/reset y PUT /api/v1/users/me/password, los dos primeros son públicos y el tercero
 * recibe la identidad solo por las cabeceras del Gateway
 */
@ExtendWith(MockitoExtension.class)
class PasswordControllerTest {

    private static final String FORGOT_URL = "/api/v1/auth/password/forgot";
    private static final String RESET_URL = "/api/v1/auth/password/reset";
    private static final String CHANGE_URL = "/api/v1/users/me/password";
    private static final String ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";
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
     * Prepara el controlador y el cliente de pruebas con el manejador global de errores y la resolución de
     * identidad
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(passwordController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new RequesterIdentityArgumentResolver())
                .build();
    }

    /**
     * Construye la petición de cambio de contraseña con la identidad de un solicitante
     *
     * @param id identificador del solicitante
     * @param role rol del solicitante
     * @param body cuerpo JSON de la petición
     * @return la petición lista para ejecutar
     */
    private MockHttpServletRequestBuilder changeRequest(UUID id, String role, String body) {
        return put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, role);
    }

    /**
     * Una solicitud de código responde 200 con el mensaje único del contrato y sin identidad en las cabeceras
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * La solicitud entrega al servicio el correo del cuerpo y el correlation id recibido
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Sin X-Correlation-ID el controlador entrega al servicio uno generado
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Un campo que el contrato no define no hace fallar la deserialización: llega registrado al servicio, que
     * lo rechaza (RG-04)
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Si el servicio rechaza el correo la respuesta es 400 con el detalle por campo
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Un JSON mal formado o sin cuerpo responde 400 sin llamar al servicio
     *
     * @param content cuerpo enviado, vacío si no se envía
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Un método distinto de POST responde 405 con el ErrorResponse y la cabecera Allow
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Un restablecimiento correcto responde 200 con el mensaje de confirmación
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void reset_validBody_returns200WithTheConfirmationMessage() throws Exception {
        mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content(RESET_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente. Inicia sesión de nuevo."))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /**
     * Los nombres del contrato se mapean a la petición: correo, codigo, nueva_contrasena y
     * confirmar_nueva_contrasena
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * El nombre de confirmación de la contraseña del registro no es el de este formulario: se registra como
     * campo desconocido y no como confirmación
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Un código incorrecto, vencido o usado responde 400 con CODIGO_INVALIDO, el mensaje genérico y sin errores
     * por campo
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Si el servicio rechaza los datos la respuesta es 400 con VALIDACION y el detalle por campo
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Un JSON mal formado responde 400 sin llamar al servicio
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void reset_malformedJson_returns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(post(RESET_URL).contentType(MediaType.APPLICATION_JSON).content("{ \"codigo\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(passwordService);
    }

    /**
     * Un método distinto de POST responde 405 con el ErrorResponse y la cabecera Allow
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Un cambio correcto responde 200 con el mensaje de confirmación
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void change_validRequest_returns200WithTheConfirmationMessage() throws Exception {
        mockMvc.perform(changeRequest(UUID.randomUUID(), "ESTUDIANTE", CHANGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente."))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /**
     * El servicio recibe la identidad de las cabeceras con todos sus roles, el cuerpo con los nombres del
     * contrato y el correlation id recibido
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
        assertEquals(EnumSet.of(UserRole.BRIGADISTA, UserRole.ESTUDIANTE), identity.getValue().roles());
        assertEquals("Campus#2026", body.getValue().getCurrentPassword());
        assertEquals("Nueva#2026x", body.getValue().getNewPassword());
        assertEquals("Nueva#2026x", body.getValue().getNewPasswordConfirmation());
    }

    /**
     * Un id de usuario en el cuerpo no cambia a quién se cambia la contraseña: se registra como campo
     * desconocido y la identidad sigue siendo la de las cabeceras
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Sin cabeceras de identidad la respuesta es 401 con el ErrorResponse y no se llama al servicio
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Una identidad incompleta o con valores inválidos responde 401 y no se llama al servicio
     *
     * @param userId valor de X-User-Id, vacío si no se envía
     * @param roles valor de X-User-Role, vacío si no se envía
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
            request = request.header(ROLE_HEADER, roles);
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(passwordService);
    }

    /**
     * Si el servicio rechaza por falta de permiso la respuesta es 403 con el ErrorResponse
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Si el servicio rechaza los datos, por ejemplo la contraseña actual incorrecta, la respuesta es 400 con el
     * detalle por campo
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Si el usuario del token ya no existe la respuesta es 404
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
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
     * Un JSON mal formado o sin cuerpo responde 400 sin llamar al servicio
     *
     * @param content cuerpo enviado, vacío si no se envía
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @ParameterizedTest
    @CsvSource(value = { "'{ \"contrasena_actual\": '", "''" })
    void change_malformedOrMissingBody_returns400WithoutCallingTheService(String content) throws Exception {
        MockHttpServletRequestBuilder request = put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE");
        if (!content.isEmpty()) {
            request = request.content(content);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(passwordService);
    }

    /**
     * Un método distinto de PUT responde 405 con el ErrorResponse y la cabecera Allow
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void change_wrongHttpMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(post(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_BODY)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "PUT"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));

        verifyNoInteractions(passwordService);
    }
}
