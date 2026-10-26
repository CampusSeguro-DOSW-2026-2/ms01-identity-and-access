package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.PasswordController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.MessageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidResetCodeException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.PasswordMismatchException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.SameAsCurrentPasswordException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.WrongCurrentPasswordException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PasswordService;

/**
 * RF-MS01-08: contrato HTTP de los endpoints de recuperacion (publicos) y de cambio autenticado
 * (identidad por X-User-Id, trazabilidad por X-Correlation-ID).
 */
@ExtendWith(MockitoExtension.class)
class PasswordControllerTest {

    private static final String FORGOT_URL = "/api/v1/auth/password/forgot";
    private static final String RESET_URL = "/api/v1/auth/password/reset";
    private static final String CHANGE_URL = "/api/v1/users/me/password";
    private static final String EMAIL = "maria.perez@mail.escuelaing.edu.co";
    private static final String NEW_PASSWORD = "Nueva#2026x";

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private PasswordController passwordController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(passwordController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String forgotBody(String correo) {
        return """
                {"correo": "%s"}
                """.formatted(correo);
    }

    private String resetBody(String codigo, String nueva, String confirmar) {
        return """
                {"correo": "%s", "codigo": "%s", "nueva_contrasena": "%s", "confirmar_nueva_contrasena": "%s"}
                """.formatted(EMAIL, codigo, nueva, confirmar);
    }

    private String changeBody(String actual, String nueva, String confirmar) {
        return """
                {"contrasena_actual": "%s", "nueva_contrasena": "%s", "confirmar_nueva_contrasena": "%s"}
                """.formatted(actual, nueva, confirmar);
    }

    // ------------------------------------------------------------- forgot

    @Test
    void forgot_validEmail_returns200AndForwardsCorrelationId() throws Exception {
        when(passwordService.requestResetCode(any(ForgotPasswordRequest.class), eq("corr-1")))
                .thenReturn(new MessageResponse("Si el correo está registrado, enviamos un código de recuperación."));

        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "corr-1")
                .content(forgotBody(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-1"))
                .andExpect(jsonPath("$.mensaje").value("Si el correo está registrado, enviamos un código de recuperación."));
    }

    @Test
    void forgot_withoutCorrelationHeader_stillWorks() throws Exception {
        when(passwordService.requestResetCode(any(ForgotPasswordRequest.class), anyString()))
                .thenReturn(new MessageResponse("ok"));

        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotBody(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    void forgot_invalidEmail_returns400WithFieldMessage() throws Exception {
        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotBody("no-es-un-correo")))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingrese un correo válido."))
                .andExpect(jsonPath("$.correlation_id").exists());

        verifyNoInteractions(passwordService);
    }

    // -------------------------------------------------------------- reset

    @Test
    void reset_validRequest_returns200AndForwardsCorrelationId() throws Exception {
        when(passwordService.resetPassword(any(ResetPasswordRequest.class), eq("corr-2")))
                .thenReturn(new MessageResponse("Contraseña actualizada correctamente. Inicia sesión de nuevo."));

        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", "corr-2")
                .content(resetBody("123456", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente. Inicia sesión de nuevo."));
    }

    @Test
    void reset_invalidCode_returns400WithGenericMessage() throws Exception {
        when(passwordService.resetPassword(any(ResetPasswordRequest.class), anyString()))
                .thenThrow(new InvalidResetCodeException());

        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("123456", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("Código incorrecto o vencido."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").exists());
    }

    @Test
    void reset_codeWithWrongFormat_returns400() throws Exception {
        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("12ab", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("codigo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Código incorrecto o vencido."));

        verifyNoInteractions(passwordService);
    }

    @Test
    void reset_weakPassword_returns400WithFieldMessage() throws Exception {
        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("123456", "debil", "debil")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("nueva_contrasena"));

        verifyNoInteractions(passwordService);
    }

    @Test
    void reset_confirmationMismatch_returns400OnConfirmField() throws Exception {
        when(passwordService.resetPassword(any(ResetPasswordRequest.class), anyString()))
                .thenThrow(new PasswordMismatchException());

        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("123456", NEW_PASSWORD, "Otra#2026xx")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("confirmar_nueva_contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Las contraseñas no coinciden."))
                .andExpect(jsonPath("$.correlation_id").exists());
    }

    // ------------------------------------------------------------- change

    @Test
    void change_validRequest_returns200AndUsesHeaderIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        when(passwordService.changePassword(eq(userId), any(ChangePasswordRequest.class), eq("corr-3")))
                .thenReturn(new MessageResponse("Contraseña actualizada correctamente."));

        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", userId.toString())
                .header("X-Correlation-ID", "corr-3")
                .content(changeBody("Actual#2026", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-3"))
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente."));

        verify(passwordService).changePassword(eq(userId), any(ChangePasswordRequest.class), eq("corr-3"));
    }

    @Test
    void change_missingUserIdHeader_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody("Actual#2026", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordService);
    }

    @Test
    void change_weakNewPassword_returns400OnNewPasswordField() throws Exception {
        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", UUID.randomUUID().toString())
                .content(changeBody("Actual#2026", "debil", "debil")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("nueva_contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("La nueva contraseña no cumple la política o es igual a la actual."));

        verifyNoInteractions(passwordService);
    }

    @Test
    void change_wrongCurrentPassword_returns400OnCurrentPasswordField() throws Exception {
        UUID userId = UUID.randomUUID();
        when(passwordService.changePassword(eq(userId), any(ChangePasswordRequest.class), anyString()))
                .thenThrow(new WrongCurrentPasswordException());

        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", userId.toString())
                .content(changeBody("Incorrecta#1", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena_actual"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La contraseña actual es incorrecta."));
    }

    @Test
    void change_newEqualsCurrent_returns400OnNewPasswordField() throws Exception {
        UUID userId = UUID.randomUUID();
        when(passwordService.changePassword(eq(userId), any(ChangePasswordRequest.class), anyString()))
                .thenThrow(new SameAsCurrentPasswordException());

        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", userId.toString())
                .content(changeBody(NEW_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("nueva_contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("La nueva contraseña no cumple la política o es igual a la actual."));
    }

    @Test
    void change_confirmationMismatch_returns400OnConfirmField() throws Exception {
        UUID userId = UUID.randomUUID();
        when(passwordService.changePassword(eq(userId), any(ChangePasswordRequest.class), anyString()))
                .thenThrow(new PasswordMismatchException());

        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", userId.toString())
                .content(changeBody("Actual#2026", NEW_PASSWORD, "Otra#2026xx")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("confirmar_nueva_contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Las contraseñas no coinciden."))
                .andExpect(jsonPath("$.correlation_id").exists());
    }
}