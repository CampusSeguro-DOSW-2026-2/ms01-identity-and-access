package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.AdminOrAuditorRequiredException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.SelfStatusChangeForbiddenException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;

class BulkUserStatusOperationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void givenValidAdminAndUsers_whenChangeUserStatus_thenUpdatesSuccessfully() {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String userRole = "ADMIN";

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setListIds(List.of(targetId));
        request.setState(UserState.INACTIVO);

        UserEntity targetUser = new UserEntity();
        targetUser.setId(targetId);
        targetUser.setState(UserState.ACTIVO);

        when(userRepository.findById(targetId)).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(targetUser);

        ChangeStatusResponse response = userService.changeUserStatus(requesterId, userRole, request);

        assertNotNull(response);
        assertNotNull(response.getResultados());
        assertEquals(1, response.getResultados().size());
        assertEquals(UserState.INACTIVO, response.getResultados().get(0).getEstadoNuevo());
        verify(userRepository, times(1)).save(targetUser);
    }

    @Test
    void givenNonAdminRole_whenChangeUserStatus_thenThrowsAdminOrAuditorRequiredException() {
        UUID requesterId = UUID.randomUUID();
        String userRole = "ESTUDIANTE";
        ChangeStatusRequest request = new ChangeStatusRequest();

        assertThrows(AdminOrAuditorRequiredException.class, () -> {
            userService.changeUserStatus(requesterId, userRole, request);
        });
    }

    @Test
    void givenSelfTargetId_whenChangeUserStatus_thenThrowsSelfStatusChangeForbiddenException() {
        UUID requesterId = UUID.randomUUID();
        String userRole = "ADMIN";

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setListIds(List.of(requesterId));
        request.setState(UserState.INACTIVO);

        assertThrows(SelfStatusChangeForbiddenException.class, () -> {
            userService.changeUserStatus(requesterId, userRole, request);
        });
    }

    @Test
    void givenNonExistentUserId_whenChangeUserStatus_thenThrowsUserNotFoundException() {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String userRole = "ADMIN";

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setListIds(List.of(targetId));
        request.setState(UserState.INACTIVO);

        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> {
            userService.changeUserStatus(requesterId, userRole, request);
        });
    }
}