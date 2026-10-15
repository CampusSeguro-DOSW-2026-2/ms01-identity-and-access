package edu.eci.dosw.login;

import edu.eci.dosw.login.dto.LoginRequest;
import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.entity.UserRol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integracion del flujo principal del MVP:
 * registrar -> iniciar sesion -> consultar perfil -> listar usuarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Flujo completo de registro, login y consulta de perfil")
    void registerLoginAndProfile_workEndToEnd() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("flujo@uni.edu", "Secreta123", UserRol.BRIGADISTA);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rol").value("BRIGADISTA"));

        LoginRequest loginRequest = new LoginRequest("flujo@uni.edu", "Secreta123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("flujo@uni.edu"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        mockMvc.perform(get("/api/v1/auth/me").header("X-Email", "flujo@uni.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flujo@uni.edu"));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email").value(org.hamcrest.Matchers.hasItem("flujo@uni.edu")));

        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(UserRol.values().length));
    }

    @Test
    @DisplayName("Registrar dos veces el mismo correo retorna 409")
    void registerDuplicateEmail_returnsConflict() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicado@uni.edu", "Secreta123", UserRol.ESTUDIANTE);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Login con contrasena incorrecta retorna 401")
    void loginWithWrongPassword_returnsUnauthorized() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("seguro@uni.edu", "Secreta123", UserRol.ESTUDIANTE);
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("seguro@uni.edu", "incorrecta"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    @DisplayName("La contrasena nunca se expone en las respuestas")
    void responses_neverExposePassword() throws Exception {
        RegisterRequest request = new RegisterRequest("privado@uni.edu", "Secreta123", UserRol.AUDITOR);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.pw").doesNotExist());
    }

    @Test
    @DisplayName("La asignacion de rol acepta el nombre de API y el valor con espacios")
    void assignRol_acceptsApiCodeAndSpacedValue() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("roles@uni.edu", "Secreta123", UserRol.ESTUDIANTE);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        String id = mockMvc.perform(get("/api/v1/auth/me").header("X-Email", "roles@uni.edu"))
                .andReturn().getResponse().getContentAsString();

        long userId = objectMapper.readTree(id).get("id").asLong();

        mockMvc.perform(post("/api/v1/users/" + userId + "/roles").param("rol", "COORDINADOR_DE_EMERGENCIAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("COORDINADOR_DE_EMERGENCIAS"));

        mockMvc.perform(post("/api/v1/users/" + userId + "/roles").param("rol", "PERSONAL ADMINISTRATIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PERSONAL_ADMINISTRATIVO"));
    }

    @Test
    @DisplayName("La asignacion de un rol inexistente retorna 400")
    void assignRol_withUnknownRole_returnsBadRequest() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("roles2@uni.edu", "Secreta123", UserRol.ESTUDIANTE);
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        String id = mockMvc.perform(get("/api/v1/auth/me").header("X-Email", "roles2@uni.edu"))
                .andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(id).get("id").asLong();

        mockMvc.perform(post("/api/v1/users/" + userId + "/roles").param("rol", "SUPER_ADMIN"))
                .andExpect(status().isBadRequest());
    }
}