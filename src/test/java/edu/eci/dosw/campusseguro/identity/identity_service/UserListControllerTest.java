package edu.eci.dosw.campusseguro.identity.identity_service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.UserController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.AdminOrAuditorRequiredException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidSearchParamException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
class UserListControllerTest {

    private static final String URL = "/api/v1/users";
    private static final String ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String ADMIN_ROLE = "ADMIN";

    @Mock private UserService userService;
    @InjectMocks private UserController userController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserResponse response(String email) {
        UserResponse r = new UserResponse();
        r.setId(UUID.randomUUID());
        r.setEmail(email);
        r.setRoles(List.of(UserRole.ESTUDIANTE));
        r.setState(UserState.ACTIVO);
        return r;
    }

    @Test
    void list_asAdmin_returns200WithPageAndFilters() throws Exception {
        when(userService.search(eq(ADMIN_ID), eq(ADMIN_ROLE), eq("ana"), eq("ACTIVO"), eq("ESTUDIANTE"), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(response("ana@mail.escuelaing.edu.co")), 0, 20, 1, 1));

        mockMvc.perform(get(URL)
                        .header(ID_HEADER, ADMIN_ID.toString())
                        .header(ROLE_HEADER, ADMIN_ROLE)
                        .param("correo", "ana").param("estado", "ACTIVO").param("rol", "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].correo").value("ana@mail.escuelaing.edu.co"))
                .andExpect(jsonPath("$.contenido[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$.contenido[0].password").doesNotExist())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.pagina").value(0));
    }

    @Test
    void list_withoutParams_usesDefaultsPage0Size10() throws Exception {
        when(userService.search(eq(ADMIN_ID), eq(ADMIN_ROLE), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(URL)
                        .header(ID_HEADER, ADMIN_ID.toString())
                        .header(ROLE_HEADER, ADMIN_ROLE))
                .andExpect(status().isOk());
        
        verify(userService).search(eq(ADMIN_ID), eq(ADMIN_ROLE), isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void list_notAdmin_returns403() throws Exception {
        when(userService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new AdminOrAuditorRequiredException());

        mockMvc.perform(get(URL)
                        .header(ID_HEADER, UUID.randomUUID().toString())
                        .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void list_invalidParam_returns400() throws Exception {
        when(userService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new InvalidSearchParamException("size debe estar entre 1 y 100"));

        mockMvc.perform(get(URL)
                        .header(ID_HEADER, ADMIN_ID.toString())
                        .header(ROLE_HEADER, ADMIN_ROLE)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_pageNotNumeric_returns400_andWithoutHeaderReturns400() throws Exception {
        // Falla por formato de página no numérico
        mockMvc.perform(get(URL)
                        .header(ID_HEADER, ADMIN_ID.toString())
                        .header(ROLE_HEADER, ADMIN_ROLE)
                        .param("page", "abc"))
                .andExpect(status().isBadRequest());
        
        // Falla por faltar cabeceras obligatorias (X-User-Id o X-User-Role)
        mockMvc.perform(get(URL)).andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }
}
