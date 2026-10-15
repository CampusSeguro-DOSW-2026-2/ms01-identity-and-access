package edu.eci.dosw.login.controller;

import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;
import edu.eci.dosw.login.dto.RegisterUpdateRequest;
import edu.eci.dosw.login.dto.RoleResponse;
import edu.eci.dosw.login.dto.UserPatchRequest;
import edu.eci.dosw.login.entity.UserRol;
import edu.eci.dosw.login.exception.EmailAlreadyExistsException;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de capa web (slice) del {@link AccessController} con MockMvc,
 * {@code @MockitoBean} y ObjectMapper.
 */
@WebMvcTest(AccessController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccessService accessService;

    @Test
    @DisplayName("GET /api/v1/users retorna la lista de usuarios")
    void findAll_returnsUsers() throws Exception {
        when(accessService.findAll()).thenReturn(List.of(
                new RegisterResponse(1L, "ana@uni.edu", UserRol.ESTUDIANTE)));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].email").value("ana@uni.edu"))
                .andExpect(jsonPath("$[0].rol").value("ESTUDIANTE"));
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} retorna el usuario encontrado")
    void findById_returnsUser() throws Exception {
        when(accessService.findById(1L)).thenReturn(new RegisterResponse(1L, "ana@uni.edu", UserRol.PROFESOR));

        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@uni.edu"))
                .andExpect(jsonPath("$.rol").value("PROFESOR"));
    }

    @Test
    @DisplayName("GET /api/v1/users/{id} retorna 404 cuando no existe")
    void findById_whenMissing_returns404() throws Exception {
        when(accessService.findById(9L)).thenThrow(new UserNotFoundException(9L));

        mockMvc.perform(get("/api/v1/users/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/users/9"));
    }

    @Test
    @DisplayName("POST /api/v1/users crea un usuario")
    void create_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest("ana@uni.edu", "Secreta123", UserRol.ESTUDIANTE);
        when(accessService.register(any(RegisterRequest.class)))
                .thenReturn(new RegisterResponse(7L, "ana@uni.edu", UserRol.ESTUDIANTE));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("POST /api/v1/users retorna 400 con un cuerpo invalido")
    void create_withInvalidBody_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("no-es-email", "corta", null);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.rol").exists());
    }

    @Test
    @DisplayName("POST /api/v1/users retorna 409 si el correo ya existe")
    void create_whenEmailExists_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("ana@uni.edu", "Secreta123", UserRol.ESTUDIANTE);
        when(accessService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("ana@uni.edu"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("PUT /api/v1/users/{id} actualiza por completo")
    void update_returns200() throws Exception {
        RegisterUpdateRequest request = new RegisterUpdateRequest("nueva@uni.edu", "Secreta456", UserRol.PROFESOR);
        when(accessService.update(any(RegisterUpdateRequest.class), eq(1L)))
                .thenReturn(new RegisterResponse(1L, "nueva@uni.edu", UserRol.PROFESOR));

        mockMvc.perform(put("/api/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("PROFESOR"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/{id} actualiza parcialmente")
    void patch_updatesPartially() throws Exception {
        UserPatchRequest request = new UserPatchRequest(null, null, UserRol.AUDITOR);
        when(accessService.patch(any(UserPatchRequest.class), eq(1L)))
                .thenReturn(new RegisterResponse(1L, "ana@uni.edu", UserRol.AUDITOR));

        mockMvc.perform(patch("/api/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("AUDITOR"));
    }

    @Test
    @DisplayName("POST /api/v1/users/{id}/roles asigna un rol valido")
    void assignRol_returnsUpdatedUser() throws Exception {
        when(accessService.assignRol(1L, UserRol.BRIGADISTA))
                .thenReturn(new RegisterResponse(1L, "ana@uni.edu", UserRol.BRIGADISTA));

        mockMvc.perform(post("/api/v1/users/1/roles").param("rol", "BRIGADISTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("BRIGADISTA"));
    }

    @Test
    @DisplayName("POST /api/v1/users/{id}/roles retorna 400 con un rol invalido")
    void assignRol_withInvalidRole_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/1/roles").param("rol", "NO_EXISTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{id} retorna 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{id} retorna 404 cuando no existe")
    void delete_whenMissing_returns404() throws Exception {
        doThrow(new UserNotFoundException(4L)).when(accessService).delete(4L);

        mockMvc.perform(delete("/api/v1/users/4"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/users/{id} con correo duplicado retorna 409")
    void create_whenEmailDuplicated_returnsConflictDetails() throws Exception {
        RegisterRequest request = new RegisterRequest("ana@uni.edu", "Secreta123", UserRol.ESTUDIANTE);
        when(accessService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("ana@uni.edu"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("GET /api/v1/roles retorna el catalogo de roles")
    void findRoles_returnsCatalog() throws Exception {
        when(accessService.findRoles()).thenReturn(List.of(
                new RoleResponse("ESTUDIANTE", "Estudiante"),
                new RoleResponse("AUDITOR", "Auditor")));

        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ESTUDIANTE"))
                .andExpect(jsonPath("$[1].name").value("Auditor"));
    }
}