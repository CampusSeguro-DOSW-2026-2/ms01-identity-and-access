package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                {"correo": "%s", "codigo": "%s", "nueva_contrasena": "%s", "confirmar_contrasena": "%s"}
                """.formatted(EMAIL, codigo, nueva, confirmar);
    }

    private String changeBody(String actual, String nueva, String confirmar) {
        return """
                {"contrasena_actual": "%s", "nueva_contrasena": "%s", "confirmar_contrasena": "%s"}
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
                .andExpect(jsonPath("$.mensaje").value("Si el correo está registrado, enviamos un código de recuperación."));
    }

    @Test
    void forgot_withoutCorrelationHeader_stillWorks() throws Exception {
        when(passwordService.requestResetCode(any(ForgotPasswordRequest.class), isNull()))
                .thenReturn(new MessageResponse("ok"));

        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotBody(EMAIL)))
                .andExpect(status().isOk());
    }

    @Test
    void forgot_invalidEmail_returns400WithFieldMessage() throws Exception {
        mockMvc.perform(post(FORGOT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotBody("no-es-un-correo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.correo").value("Ingrese un correo válido."));

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
        when(passwordService.resetPassword(any(ResetPasswordRequest.class), isNull()))
                .thenThrow(new InvalidResetCodeException());

        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("123456", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Código incorrecto o vencido."));
    }

    @Test
    void reset_codeWithWrongFormat_returns400() throws Exception {
        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("12ab", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("Código incorrecto o vencido."));

        verifyNoInteractions(passwordService);
    }

    @Test
    void reset_weakPassword_returns400WithFieldMessage() throws Exception {
        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("123456", "debil", "debil")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.nueva_contrasena").exists());

        verifyNoInteractions(passwordService);
    }

    @Test
    void reset_confirmationMismatch_returns400OnConfirmField() throws Exception {
        when(passwordService.resetPassword(any(ResetPasswordRequest.class), isNull()))
                .thenThrow(new PasswordMismatchException());

        mockMvc.perform(post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetBody("123456", NEW_PASSWORD, "Otra#2026xx")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.confirmar_contrasena").value("Las contraseñas no coinciden."));
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
                .andExpect(jsonPath("$.nueva_contrasena")
                        .value("La nueva contraseña no cumple la política o es igual a la actual."));

        verifyNoInteractions(passwordService);
    }

    @Test
    void change_wrongCurrentPassword_returns400OnCurrentPasswordField() throws Exception {
        UUID userId = UUID.randomUUID();
        when(passwordService.changePassword(eq(userId), any(ChangePasswordRequest.class), isNull()))
                .thenThrow(new WrongCurrentPasswordException());

        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", userId.toString())
                .content(changeBody("Incorrecta#1", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.contrasena_actual").value("La contraseña actual es incorrecta."));
    }

    @Test
    void change_newEqualsCurrent_returns400OnNewPasswordField() throws Exception {
        UUID userId = UUID.randomUUID();
        when(passwordService.changePassword(eq(userId), any(ChangePasswordRequest.class), isNull()))
                .thenThrow(new SameAsCurrentPasswordException());

        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", userId.toString())
                .content(changeBody(NEW_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.nueva_contrasena")
                        .value("La nueva contraseña no cumple la política o es igual a la actual."));
    }

    @Test
    void change_confirmationMismatch_returns400OnConfirmField() throws Exception {
        UUID userId = UUID.randomUUID();
        when(passwordService.changePassword(eq(userId), any(ChangePasswordRequest.class), isNull()))
                .thenThrow(new PasswordMismatchException());

        mockMvc.perform(put(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", userId.toString())
                .content(changeBody("Actual#2026", NEW_PASSWORD, "Otra#2026xx")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.confirmar_contrasena").value("Las contraseñas no coinciden."));
    }
}