package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.AdminOrAuditorRequiredException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidSearchParamException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
public class UserSearchServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserServiceImpl userService;

    private final UUID adminId = UUID.randomUUID();
    private final String ADMIN_ROLE = "ADMIN";
    private final String STUDENT_ROLE = "ESTUDIANTE";

    private UserEntity user(UUID id, String email, UserState state, UserRole... roles) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setFirstName("Carlos");
        u.setFirstLastName("Gomez");
        u.setEmail(email);
        u.setPassword("$2a$10$hash");
        u.setRoles(List.of(roles));
        u.setState(state);
        return u;
    }

    @Test
    void search_asAdmin_returnsPageAndUsesPaginationAndEmailOrder() {
        UserEntity a = user(UUID.randomUUID(), "ana@mail.escuelaing.edu.co", UserState.ACTIVO, UserRole.ESTUDIANTE);
        UserEntity b = user(UUID.randomUUID(), "beto@mail.escuelaing.edu.co", UserState.INACTIVO, UserRole.PROFESOR);
        
        when(userRepository.searchUsers(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(1, 2), 5));

        // Se envía el rol directamente por parámetro en memoria
        PageResponse<UserResponse> result = userService.search(adminId, ADMIN_ROLE, "an", "ACTIVO", "ESTUDIANTE", 1, 2);

        assertEquals(2, result.getContenido().size());
        assertEquals(5, result.getTotalElementos());
        assertEquals(3, result.getTotalPaginas());
        assertEquals(1, result.getPagina());
        assertEquals(UserState.INACTIVO, result.getContenido().get(1).getState());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchUsers(any(), any(), any(), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(2, captor.getValue().getPageSize());
        assertTrue(captor.getValue().getSort().getOrderFor("email").isAscending());
    }

    @Test
    void search_todosAndNullFilters_areAccepted() {
        when(userRepository.searchUsers(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertDoesNotThrow(() -> userService.search(adminId, ADMIN_ROLE, null, "TODOS", "todos", 0, 20));
    }

    @Test
    void search_requesterIsNotAdmin_throwsAndDoesNotQuery() {
        // Al no tener rol ADMIN o AUDITOR, debe lanzar excepción sin tocar la base de datos
        assertThrows(AdminOrAuditorRequiredException.class, () -> userService.search(adminId, STUDENT_ROLE, null, null, null, 0, 20));
        verify(userRepository, never()).searchUsers(any(), any(), any(), any(Pageable.class));
    }

    @Test
    void search_nullRole_isRejected() {
        assertThrows(AdminOrAuditorRequiredException.class, () -> userService.search(adminId, null, null, null, null, 0, 20));
    }

    @Test
    void search_invalidFilters_throw() {
        assertThrows(InvalidSearchParamException.class, () -> userService.search(adminId, ADMIN_ROLE, null, "XYZ", null, 0, 20));
        assertThrows(InvalidSearchParamException.class, () -> userService.search(adminId, ADMIN_ROLE, null, null, "SUPERMAN", 0, 20));
    }
}
