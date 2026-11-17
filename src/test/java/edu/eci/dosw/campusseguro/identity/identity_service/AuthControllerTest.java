package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

/**
 * Tests of POST /api/v1/auth/register (RF-MS01-01, form F-01), the validation of the fields is done by the service and
 * is tested in UserServiceTest
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String URL = "/api/v1/auth/register";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    @Mock
    private UserService userService;

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
     * Builds the response of a sample registered user
     *
     * @return the response of the registration
     */
    private UserResponse buildResponse() {
        return new UserResponse(UUID.randomUUID(), "carlos.gomez@escuelaing.edu.co",
                List.of("ESTUDIANTE"), UserState.ACTIVO);
    }

        /**
     * A successful registration answers 201 with the fields of the contract and without a password
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_validRequest_returns201WithTheContractFieldsAndWithoutPassword() throws Exception {
        when(userService.create(any(UserCreateRequest.class), eq(CORRELATION_ID))).thenReturn(buildResponse());

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(body()))
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
     * The field names of the contract are assigned to the request that the service receives
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_mapsTheWireNamesToTheRequest() throws Exception {
        when(userService.create(any(UserCreateRequest.class), any())).thenReturn(buildResponse());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated());

        ArgumentCaptor<UserCreateRequest> captor = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService).create(captor.capture(), any());
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
        when(userService.create(any(UserCreateRequest.class), any()))
                .thenThrow(new EmailAlreadyExistsException());

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
        when(userService.create(any(UserCreateRequest.class), any())).thenThrow(new InvalidRequestException(
                "Datos inválidos.", List.of(
                        new ErrorResponse.ErrorDetail("correo", "Correo no válido."),
                        new ErrorResponse.ErrorDetail("confirmar_contrasena", "Las contraseñas no coinciden."))));

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

        verifyNoInteractions(userService);
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

        verifyNoInteractions(userService);
    }

        /**
     * The registration is public and does not require the identity headers
     *
     * @throws Exception if the simulated request or the reading of the response fails
     */
    @Test
    void register_isPublic_itDoesNotRequireTheIdentityHeaders() throws Exception {
        when(userService.create(any(UserCreateRequest.class), any())).thenReturn(buildResponse());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated());
    }
}
