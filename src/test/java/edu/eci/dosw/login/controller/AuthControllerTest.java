package edu.eci.dosw.login.controller;

import edu.eci.dosw.login.dto.LoginRequest;
import edu.eci.dosw.login.dto.LoginResponse;
import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;
import edu.eci.dosw.login.entity.UserRol;
import edu.eci.dosw.login.exception.InvalidCredentialsException;
import edu.eci.dosw.login.exception.UserNotFoundException;
import edu.eci.dosw.login.service.AccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de capa web (slice) del {@link AuthController} con MockMvc,
 * {@code @MockitoBean} y ObjectMapper.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccessService accessService;

    @Test
    @DisplayName("POST /api/v1/auth/register crea el usuario y retorna 201")
    void register_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest("ana@uni.edu", "Secreta123", UserRol.ESTUDIANTE);
        when(accessService.register(any(RegisterRequest.class)))
                .thenReturn(new RegisterResponse(1L, "ana@uni.edu", UserRol.ESTUDIANTE));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("ana@uni.edu"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register retorna 400 si el cuerpo es invalido")
    void register_withInvalidBody_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("correo-invalido", "123", null);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.password").exists())
                .andExpect(jsonPath("$.details.rol").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login retorna la sesion autenticada")
    void login_withValidCredentials_returnsSession() throws Exception {
        LoginRequest request = new LoginRequest("ana@uni.edu", "Secreta123");
        when(accessService.login(any(LoginRequest.class))).thenReturn(
                LoginResponse.authenticated(new RegisterResponse(1L, "ana@uni.edu", UserRol.ESTUDIANTE)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("ana@uni.edu"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login retorna 401 con credenciales invalidass")
    void login_withInvalidCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("ana@uni.edu", "mala");
        when(accessService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me retorna el perfil del usuario de la cabecera")
    void me_returnsProfile() throws Exception {
        when(accessService.findByEmail("ana@uni.edu"))
                .thenReturn(new RegisterResponse(1L, "ana@uni.edu", UserRol.PROFESOR));

        mockMvc.perform(get("/api/v1/auth/me").header("X-Email", "ana@uni.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@uni.edu"))
                .andExpect(jsonPath("$.rol").value("PROFESOR"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me retorna 400 si falta la cabecera X-Email")
    void me_withoutHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me retorna 404 si el usuario no existe")
    void me_whenUserMissing_returns404() throws Exception {
        when(accessService.findByEmail("nadie@uni.edu")).thenThrow(new UserNotFoundException("nadie@uni.edu"));

        mockMvc.perform(get("/api/v1/auth/me").header("X-Email", "nadie@uni.edu"))
                .andExpect(status().isNotFound());
    }
}