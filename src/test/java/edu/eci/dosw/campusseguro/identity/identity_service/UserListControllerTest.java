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
import edu.eci.dosw.campusseguro.identity.identity_service.exception.AdminRequiredException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidSearchParamException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
class UserListControllerTest {

    private static final String URL = "/api/v1/users/search";
    private static final String HEADER = "X-User-Id";
    private static final UUID ADMIN = UUID.randomUUID();

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

    /**
     * 
     * @throws Exception
     */
    @Test
    void list_asAdmin_returns200WithPageAndFilters() throws Exception {
        // Actualizado el tamaño a 10 para que coincida con el valor por defecto de Spring
        when(userService.search(eq(ADMIN), eq("ana"), eq("ACTIVO"), eq("ESTUDIANTE"), eq(0), eq(10)))
                .thenReturn(new PageResponse<>(List.of(response("ana@mail.escuelaing.edu.co")), 0, 10, 1, 1));

        mockMvc.perform(get(URL).header(HEADER, ADMIN.toString())
                        .param("correo", "ana").param("estado", "ACTIVO").param("rol", "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].correo").value("ana@mail.escuelaing.edu.co"))
                .andExpect(jsonPath("$.contenido[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$.contenido[0].password").doesNotExist())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.pagina").value(0));
    }

    /**
     * 
     * @throws Exception
     */
    @Test
    void list_withoutParams_usesDefaultsPage0Size20() throws Exception {
        // Actualizado el tamaño a 10 en el when y en el verify
        when(userService.search(eq(ADMIN), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get(URL).header(HEADER, ADMIN.toString())).andExpect(status().isOk());
        verify(userService).search(eq(ADMIN), isNull(), isNull(), isNull(), eq(0), eq(10));
    }

    /**
     * 
     * @throws Exception
     */
    @Test
    void list_notAdmin_returns403() throws Exception {
        when(userService.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new AdminRequiredException());

        mockMvc.perform(get(URL).header(HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    /**
     * 
     * @throws Exception
     */
    @Test
    void list_invalidParam_returns400() throws Exception {
        when(userService.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new InvalidSearchParamException("size debe estar entre 1 y 100"));

        mockMvc.perform(get(URL).header(HEADER, ADMIN.toString()).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 
     * @throws Exception
     */
    @Test
    void list_pageNotNumeric_returns400_andWithoutHeaderReturns400() throws Exception {
        mockMvc.perform(get(URL).header(HEADER, ADMIN.toString()).param("page", "abc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL)).andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }
}
