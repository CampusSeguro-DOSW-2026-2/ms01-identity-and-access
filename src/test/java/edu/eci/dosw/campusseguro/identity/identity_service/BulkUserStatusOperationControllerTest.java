package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.UserController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.IndividualChangeStateResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.AdminOrAuditorRequiredException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.SelfStatusChangeForbiddenException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

@ExtendWith(MockitoExtension.class)
class BulkUserStatusControllerTest {

    private static final String URL = "/api/v1/users/status";

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void changeUserStatus_validAdminAndUsers_returns200() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String userRole = "ADMIN";

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setListIds(List.of(targetId));
        request.setState(UserState.INACTIVO);

        UserEntity targetUser = new UserEntity();
        targetUser.setId(targetId);
        targetUser.setState(UserState.INACTIVO);

        IndividualChangeStateResponse individualResponse = IndividualChangeStateResponse.fromEntity(targetUser, UserState.ACTIVO);
        ChangeStatusResponse mockResponse = ChangeStatusResponse.fromEntity(List.of(individualResponse));

        when(userService.changeUserStatus(eq(requesterId), eq(userRole), any(ChangeStatusRequest.class)))
                .thenReturn(mockResponse);

        String jsonBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(patch(URL)
                .header("X-User-Id", requesterId.toString())
                .header("X-User-Role", userRole)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].id").value(targetId.toString()))
                .andExpect(jsonPath("$.resultados[0].estado_anterior").value("ACTIVO"))
                .andExpect(jsonPath("$.resultados[0].estado_nuevo").value("INACTIVO"));
    }

    @Test
    void changeUserStatus_unauthorizedRole_returnsForbidden() throws Exception {
        UUID requesterId = UUID.randomUUID();
        String userRole = "ESTUDIANTE";

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setListIds(List.of(UUID.randomUUID()));
        request.setState(UserState.INACTIVO);

        when(userService.changeUserStatus(eq(requesterId), eq(userRole), any(ChangeStatusRequest.class)))
                .thenThrow(new AdminOrAuditorRequiredException());

        String jsonBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(patch(URL)
                .header("X-User-Id", requesterId.toString())
                .header("X-User-Role", userRole)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeUserStatus_selfModification_returnsForbidden() throws Exception {
        UUID requesterId = UUID.randomUUID();
        String userRole = "ADMIN";

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setListIds(List.of(requesterId)); // Intenta cambiarse a sí mismo
        request.setState(UserState.INACTIVO);

        when(userService.changeUserStatus(eq(requesterId), eq(userRole), any(ChangeStatusRequest.class)))
                .thenThrow(new SelfStatusChangeForbiddenException());

        String jsonBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(patch(URL)
                .header("X-User-Id", requesterId.toString())
                .header("X-User-Role", userRole)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeUserStatus_userNotFound_returnsNotFound() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String userRole = "ADMIN";

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setListIds(List.of(targetId));
        request.setState(UserState.INACTIVO);

        when(userService.changeUserStatus(eq(requesterId), eq(userRole), any(ChangeStatusRequest.class)))
                .thenThrow(new UserNotFoundException(targetId));

        String jsonBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(patch(URL)
                .header("X-User-Id", requesterId.toString())
                .header("X-User-Role", userRole)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isNotFound());
    }
}
