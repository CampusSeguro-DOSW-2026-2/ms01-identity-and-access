package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InactiveAccountException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidCredentialsException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

/**
 * POST /api/v1/auth/login (RF-MS01-02, formulario F-02): contrato HTTP segun el OpenAPI.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLoginTest {

    private static final String URL = "/api/v1/auth/login";
    private static final String EMAIL = "maria.perez@mail.escuelaing.edu.co";
    private static final String PASSWORD = "Campus#2026";

    @Mock
    private UserService userService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String body(String correo, String contrasena) {
        return """
                {
                  "correo": "%s",
                  "contrasena": "%s"
                }
                """.formatted(correo, contrasena);
    }

    private LoginResponse buildResponse(UUID id) {
        return new LoginResponse(
                "header.payload.signature",
                "Bearer",
                "2026-10-22T06:00:00Z",
                new LoginResponse.UsuarioResumen(id, EMAIL, List.of(UserRole.ESTUDIANTE, UserRole.PROFESOR)));
    }

    private void assertValidationError(String json, String campo, String mensaje) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[?(@.campo == '" + campo + "')].mensaje").value(mensaje));
        verifyNoInteractions(authService);
    }

    // ---------------------------------------------------------------- 200

    @Test
    void login_validCredentials_returns200WithTokenAndBasicUserData() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any(LoginRequest.class), eq("corr-1"))).thenReturn(buildResponse(id));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "corr-1")
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

    @Test
    void login_normalizesEmailButNeverTouchesThePassword() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenReturn(buildResponse(UUID.randomUUID()));

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("  Maria.Perez@Mail.EscuelaIng.edu.co ", " Campus#2026 ")))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(captor.capture(), isNull());
        assertEquals(EMAIL, captor.getValue().getCorreo());
        assertEquals(" Campus#2026 ", captor.getValue().getContrasena());
    }

    // ---------------------------------------------------------------- 401

    @Test
    void login_invalidCredentials_returns401WithNormalizedBody() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "corr-1")
                .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INCORRECTAS"))
                .andExpect(jsonPath("$.mensaje").value("Credenciales incorrectas."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").value("corr-1"));
    }

    @Test
    void login_invalidCredentials_responseDoesNotDependOnTheRequest() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(new InvalidCredentialsException());

        String unknownEmail = mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("nadie@mail.escuelaing.edu.co", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrongPassword = mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(EMAIL, "Incorrecta#1")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(unknownEmail, wrongPassword);
    }

    @Test
    void login_withoutCorrelationHeader_omitsCorrelationIdFromErrorBody() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlation_id").doesNotExist());
    }

    // ---------------------------------------------------------------- 403

    @Test
    void login_inactiveAccount_returns403WithNormalizedBody() throws Exception {
        when(authService.login(any(LoginRequest.class), any())).thenThrow(new InactiveAccountException());

        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "corr-2")
                .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("CUENTA_INACTIVA"))
                .andExpect(jsonPath("$.mensaje").value("Cuenta inactiva; contacte al administrador si desea reactivarla."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").value("corr-2"));
    }

    // ---------------------------------------------------------------- 400

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        assertValidationError(body("no-es-un-correo", PASSWORD), "correo", "Ingrese un correo válido.");
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        assertValidationError(body("   ", PASSWORD), "correo", "Ingrese un correo válido.");
    }

    @Test
    void login_emailLongerThan100_returns400() throws Exception {
        String tooLong = "a".repeat(64) + "@" + "b".repeat(29) + ".edu.co"; // 101 caracteres
        assertValidationError(body(tooLong, PASSWORD), "correo", "Ingrese un correo válido.");
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        assertValidationError(body(EMAIL, ""), "contrasena", "Ingrese su contraseña.");
    }

    @Test
    void login_passwordWithOnlySpaces_isTreatedAsMissing() throws Exception {
        assertValidationError(body(EMAIL, "   "), "contrasena", "Ingrese su contraseña.");
    }

    @Test
    void login_passwordLongerThan64_returns400() throws Exception {
        assertValidationError(body(EMAIL, "a".repeat(65)), "contrasena",
                "La contraseña no puede superar los 64 caracteres.");
    }

    @Test
    void login_missingBothFields_reportsBothFieldsInStableOrder() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Datos inválidos."))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingrese su contraseña."))
                .andExpect(jsonPath("$.errores[1].campo").value("correo"))
                .andExpect(jsonPath("$.errores[1].mensaje").value("Ingrese un correo válido."));
        verifyNoInteractions(authService);
    }

    @Test
    void login_validationErrorEchoesCorrelationId() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "corr-3")
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.correlation_id").value("corr-3"));
    }

    @Test
    void login_fieldNotDefinedInTheContract_returns400() throws Exception {
        String json = """
                {
                  "correo": "%s",
                  "contrasena": "%s",
                  "rol": "ADMIN"
                }
                """.formatted(EMAIL, PASSWORD);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Datos inválidos."));
        verifyNoInteractions(authService);
    }

    @Test
    void login_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verifyNoInteractions(authService);
    }

    @Test
    void login_missingBody_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verifyNoInteractions(authService);
    }
}
