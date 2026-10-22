package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String URL = "/api/v1/users";

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserResponse buildResponse(UUID id, String email) {
        UserResponse response = new UserResponse();
        response.setId(id);
        response.setEmail(email);
        response.setRoles(List.of(UserRole.ESTUDIANTE));
        response.setState(UserState.ACTIVO);
        return response;
    }

    @Test
    void findAll_returns200WithUserList() throws Exception {
        when(userService.findAll()).thenReturn(List.of(
                buildResponse(UUID.randomUUID(), "uno@mail.escuelaing.edu.co"),
                buildResponse(UUID.randomUUID(), "dos@mail.escuelaing.edu.co")));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].correo").value("uno@mail.escuelaing.edu.co"))
                .andExpect(jsonPath("$[1].correo").value("dos@mail.escuelaing.edu.co"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void findAll_noUsers_returns200WithEmptyList() throws Exception {
        when(userService.findAll()).thenReturn(List.of());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findById_existingUser_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findById(id)).thenReturn(buildResponse(id, "carlos.gomez@mail.escuelaing.edu.co"));

        mockMvc.perform(get(URL + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.correo").value("carlos.gomez@mail.escuelaing.edu.co"));
    }

    @Test
    void findById_unknownUser_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findById(id)).thenThrow(new UserNotFoundException(id));

        mockMvc.perform(get(URL + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void findById_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get(URL + "/{id}", "no-es-un-uuid"))
                .andExpect(status().isBadRequest());
    }
}