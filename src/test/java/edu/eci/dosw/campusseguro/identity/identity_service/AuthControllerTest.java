package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.AuthController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResendRegistrationCodeRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.VerifyRegistrationRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidVerificationCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RegistrationService;

/**
 * Tests of POST /api/v1/auth/register, /register/verify and /register/resend (RF-MS01-01, forms F-01 and F-14), the
 * validation of the fields is done by the service and is tested in RegistrationServiceTest
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String URL = "/api/v1/auth/register";
    private static final String VERIFY_URL = "/api/v1/auth/register/verify";
    private static final String RESEND_URL = "/api/v1/auth/register/resend";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    @Mock
    private RegistrationService registrationService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

        /**
     * Prepares the controller and the test client with the global error handler
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

        /**
     * Builds the JSON body of a valid registration
     *
     * @return the JSON body of the request
     */
    private String body() {
        return """
                {
                  "primer_nombre": "Carlos",
                  "segundo_nombre": "Andres",
                  "primer_apellido": "Gomez",
                  "segundo_apellido": "Perez",
                  "correo": "carlos.gomez@escuelaing.edu.co",
                  "contrasena": "MiPassword#2026",
                  "confirmar_contrasena": "MiPassword#2026"
                }
                """;
    }

        /**
     * Builds the JSON body of a verification
     *
     * @return the JSON body of the request
     */
    private String verifyBody() {
        return """
                {
                  "correo": "carlos.gomez@escuelaing.edu.co",
                  "codigo": "123456",
                  "token_registro": "token-de-registro"
                }
                """;
    }

        /**
     * Builds the response of a sample registered user
     *
     * @return the response of the registration
     */
    private UserResponse buildResponse() {
        return new UserResponse(UUID.randomUUID(), "carlos.gomez@escuelaing.edu.co",
                List.of("ESTUDIANTE"), UserState.ACTIVO);
    }

        /**
     * A valid registration answers 202 with the message of the code sent, the user is not created yet
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_validRequest_returns202WithTheCodeSentMessage() throws Exception {
        when(registrationService.requestRegistration(any(UserCreateRequest.class), eq(CORRELATION_ID)))
                .thenReturn("token-de-registro");

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(body()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(AuthController.REGISTER_MESSAGE))
                .andExpect(jsonPath("$.token_registro").value("token-de-registro"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.contrasena").doesNotExist());

        verify(registrationService).requestRegistration(any(UserCreateRequest.class), eq(CORRELATION_ID));
    }

        /**
     * The field names of the contract are assigned to the request that the service receives
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_mapsTheWireNamesToTheRequest() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isAccepted());

        ArgumentCaptor<UserCreateRequest> captor = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(registrationService).requestRegistration(captor.capture(), any());
        UserCreateRequest request = captor.getValue();
        assertEquals("Carlos", request.getFirstName());
        assertEquals("Andres", request.getSecondName());
        assertEquals("Gomez", request.getFirstLastName());
        assertEquals("Perez", request.getSecondLastName());
        assertEquals("carlos.gomez@escuelaing.edu.co", request.getEmail());
        assertEquals("MiPassword#2026", request.getPassword());
        assertEquals("MiPassword#2026", request.getPasswordConfirmation());
    }

        /**
     * An already registered email answers 409 with the conflict code
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_emailAlreadyExists_returns409() throws Exception {
        doThrow(new EmailAlreadyExistsException()).when(registrationService)
                .requestRegistration(any(UserCreateRequest.class), any());

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(body()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").value("El correo ya está registrado."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));
    }

        /**
     * The validation errors of the service answer 400 with the detail by field
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_validationErrorsFromTheService_return400() throws Exception {
        doThrow(new InvalidRequestException(
                "Datos inválidos.", List.of(
                        new ErrorResponse.ErrorDetail("correo", "Correo no válido."),
                        new ErrorResponse.ErrorDetail("confirmar_contrasena", "Las contraseñas no coinciden."))))
                .when(registrationService).requestRegistration(any(UserCreateRequest.class), any());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                .andExpect(jsonPath("$.errores[1].campo").value("confirmar_contrasena"));
    }

        /**
     * A malformed JSON answers 400 without calling the service
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_malformedJson_returns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{ \"correo\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(registrationService);
    }

        /**
     * A request without a body answers 400 without calling the service
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_missingBody_returns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }

        /**
     * The registration is public and does not require the identity headers
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_isPublic_itDoesNotRequireTheIdentityHeaders() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isAccepted());
    }

        /**
     * A correct code answers 201 with the created user, with the fields of the contract and without a password
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void verify_correctCode_returns201WithTheCreatedUserAndWithoutPassword() throws Exception {
        when(registrationService.verify(any(VerifyRegistrationRequest.class), eq(CORRELATION_ID)))
                .thenReturn(buildResponse());

        mockMvc.perform(post(VERIFY_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(verifyBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.correo").value("carlos.gomez@escuelaing.edu.co"))
                .andExpect(jsonPath("$.roles[0]").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

        /**
     * The field names of the contract are assigned to the verification request
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void verify_mapsTheWireNamesToTheRequest() throws Exception {
        when(registrationService.verify(any(VerifyRegistrationRequest.class), any())).thenReturn(buildResponse());

        mockMvc.perform(post(VERIFY_URL).contentType(MediaType.APPLICATION_JSON).content(verifyBody()))
                .andExpect(status().isCreated());

        ArgumentCaptor<VerifyRegistrationRequest> captor = ArgumentCaptor.forClass(VerifyRegistrationRequest.class);
        verify(registrationService).verify(captor.capture(), any());
        assertEquals("carlos.gomez@escuelaing.edu.co", captor.getValue().getEmail());
        assertEquals("123456", captor.getValue().getCode());
        assertEquals("token-de-registro", captor.getValue().getToken());
    }

        /**
     * An incorrect or expired code answers 400 CODIGO_INVALIDO with the generic message
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void verify_invalidCode_returns400CodigoInvalido() throws Exception {
        when(registrationService.verify(any(VerifyRegistrationRequest.class), any()))
                .thenThrow(new InvalidVerificationCodeException());

        mockMvc.perform(post(VERIFY_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(verifyBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("Código incorrecto o vencido."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));
    }

        /**
     * An email that became a user while the registration was pending answers 409
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void verify_emailAlreadyRegistered_returns409() throws Exception {
        when(registrationService.verify(any(VerifyRegistrationRequest.class), any()))
                .thenThrow(new EmailAlreadyExistsException());

        mockMvc.perform(post(VERIFY_URL).contentType(MediaType.APPLICATION_JSON).content(verifyBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

        /**
     * A malformed JSON in the verification answers 400 without calling the service
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void verify_malformedJson_returns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(post(VERIFY_URL).contentType(MediaType.APPLICATION_JSON).content("{ \"codigo\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(registrationService);
    }

        /**
     * The resending answers 200 with the fixed message, whether or not the code is sent
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void resend_anyEmail_returns200WithTheFixedMessage() throws Exception {
        mockMvc.perform(post(RESEND_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content("{\"correo\": \"Carlos.Gomez@EscuelaIng.edu.co\", \"token_registro\": \"token-de-registro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(AuthController.RESEND_MESSAGE));

        ArgumentCaptor<ResendRegistrationCodeRequest> captor =
                ArgumentCaptor.forClass(ResendRegistrationCodeRequest.class);
        verify(registrationService).resendCode(captor.capture(), eq(CORRELATION_ID));
        assertEquals("carlos.gomez@escuelaing.edu.co", captor.getValue().getEmail());
        assertEquals("token-de-registro", captor.getValue().getToken());
    }

        /**
     * An invalid email in the resending answers 400 with the detail by field
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void resend_invalidEmail_returns400() throws Exception {
        doThrow(new InvalidRequestException("Datos inválidos.",
                List.of(new ErrorResponse.ErrorDetail("correo", "Ingrese un correo válido."))))
                .when(registrationService).resendCode(any(ResendRegistrationCodeRequest.class), any());

        mockMvc.perform(post(RESEND_URL).contentType(MediaType.APPLICATION_JSON).content("{\"correo\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("correo"));
    }
}
