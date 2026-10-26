package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

/**
 * POST /api/v1/auth/login (RF-MS01-02, formulario F-02): contrato HTTP según el OpenAPI, la validación de
 * los campos la hace el servicio y se prueba en AuthServiceTest y LoginRequestValidationTest
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLoginTest {

    private static final String URL = "/api/v1/auth/login";
    private static final String EMAIL = "maria.perez@escuelaing.edu.co";
    private static final String PASSWORD = "Campus#2026";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    @Mock
    private UserService userService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    /**
     * Prepara el controlador y el cliente de pruebas con el manejador global de errores
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Construye el cuerpo JSON de la petición de login
     *
     * @param correo correo que se envía
     * @param contrasena contraseña que se envía
     * @return el cuerpo JSON de la petición
     */
    private String body(String correo, String contrasena) {
        return """
                {
                  "correo": "%s",
                  "contrasena": "%s"
                }
                """.formatted(correo, contrasena);
    }

    /**
     * Construye una respuesta de login de ejemplo
     *
     * @param id identificador del usuario autenticado
     * @return la respuesta de login con un token de ejemplo
     */
    private LoginResponse buildResponse(UUID id) {
        return new LoginResponse(
                "header.payload.signature",
                "Bearer",
                "2026-10-22T06:00:00Z",
                new LoginResponse.UsuarioResumen(id, EMAIL, List.of(UserRole.ESTUDIANTE, UserRole.PROFESOR)));
    }

    /**
     * Un login correcto responde 200 con el token y los datos básicos del usuario
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_validCredentials_returns200WithTokenAndBasicUserData() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any(LoginRequest.class), eq(CORRELATION_ID))).thenReturn(buildResponse(id));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("header.payload.signature"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expira_en").value("2026-10-22T06:00:00Z"))
                .andExpect(jsonPath("$.usuario.id").value(id.toString()))
                .andExpect(jsonPath("$.usuario.correo").value(EMAIL))
                .andExpect(jsonPath("$.usuario.roles[0]").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.usuario.roles[1]").value("PROFESOR"));
    }

    /**
     * La respuesta del login nunca incluye la contraseña ni su hash
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_responseNeverExposesPasswordOrHash() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenReturn(buildResponse(UUID.randomUUID()));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.usuario.contrasena").doesNotExist())
                .andExpect(jsonPath("$.usuario.password").doesNotExist())
                .andExpect(jsonPath("$.usuario.password_hash").doesNotExist());
    }

    /**
     * Al leer el cuerpo el correo se normaliza y la contraseña se entrega sin cambios (RG-01)
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_normalizesEmailButNeverTouchesThePassword() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenReturn(buildResponse(UUID.randomUUID()));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("  Maria.Perez@EscuelaIng.edu.co ", " Campus#2026 ")))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture(), any());
        assertEquals(EMAIL, captor.getValue().getCorreo());
        assertEquals(" Campus#2026 ", captor.getValue().getContrasena());
    }

    /**
     * Sin cabecera de correlación se entrega al servicio un UUID generado
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_withoutCorrelationHeader_passesAGeneratedUuidToTheService() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenReturn(buildResponse(UUID.randomUUID()));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(EMAIL, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(authService).login(any(LoginRequest.class), captor.capture());
        assertEquals(36, captor.getValue().length());
        assertEquals(4, UUID.fromString(captor.getValue()).version());
    }

    /**
     * Unas credenciales incorrectas responden 401 con el cuerpo de error normalizado
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_invalidCredentials_returns401WithNormalizedBody() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INCORRECTAS"))
                .andExpect(jsonPath("$.mensaje").value("Credenciales incorrectas."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));
    }

    /**
     * La respuesta a credenciales incorrectas es la misma exista o no el correo
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_invalidCredentials_responseDoesNotDependOnTheRequest() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(new InvalidCredentialsException());

        String unknownEmail = mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(body("nadie@escuelaing.edu.co", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrongPassword = mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(body(EMAIL, "Incorrecta#1")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(unknownEmail, wrongPassword);
    }

    /**
     * Un error sin cabecera de correlación lleva en el cuerpo un UUID v4 generado
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_errorWithoutCorrelationHeader_generatesAUuidV4ForTheErrorBody() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(new InvalidCredentialsException());

        String json = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlation_id").exists())
                .andReturn().getResponse().getContentAsString();

        assertTrue(json.matches(".*\"correlation_id\":\"[0-9a-f-]{36}\".*"));
    }

    /**
     * Una cuenta inactiva responde 403 con el cuerpo de error normalizado
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_inactiveAccount_returns403WithNormalizedBody() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(new InactiveAccountException());

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("CUENTA_INACTIVA"))
                .andExpect(jsonPath("$.mensaje").value("Cuenta inactiva. Contacte al administrador."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));
    }

    /**
     * Los errores de validación del servicio responden 400 con una entrada por campo
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_validationErrorsFromTheService_return400WithOneEntryPerField() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(new InvalidRequestException(
                "Datos inválidos.", List.of(
                        new ErrorResponse.ErrorDetail("contrasena", "Ingrese su contraseña."),
                        new ErrorResponse.ErrorDetail("correo", "Ingrese un correo válido."))));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", CORRELATION_ID)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Datos inválidos."))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[1].campo").value("correo"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));
    }

    /**
     * Un campo que no está en el contrato se captura para que el servicio lo rechace (RG-04)
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_fieldNotDefinedInTheContract_isCapturedForTheServiceToReject() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenReturn(buildResponse(UUID.randomUUID()));
        String json = """
                {
                  "correo": "%s",
                  "contrasena": "%s",
                  "rol": "ADMIN"
                }
                """.formatted(EMAIL, PASSWORD);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture(), any());
        assertTrue(captor.getValue().unknownFieldNames().contains("rol"));
    }

    /**
     * Un JSON mal formado responde 400
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Datos inválidos."));
        verifyNoInteractions(authService);
    }

    /**
     * Una petición sin cuerpo responde 400
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_missingBody_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verifyNoInteractions(authService);
    }

    /**
     * Un tipo de contenido distinto de JSON responde 415
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_unsupportedMediaType_returns415() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("correo=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"));
        verifyNoInteractions(authService);
    }

    /**
     * Un método HTTP no permitido responde 405 con la cabecera Allow
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void login_wrongHttpMethod_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
        verifyNoInteractions(authService);
    }
}
