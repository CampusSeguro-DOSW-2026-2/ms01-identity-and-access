package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks 
    private UserServiceImpl userService;

    private UserCreateRequest buildRequest(String email) {
        UserCreateRequest request = new UserCreateRequest();
        request.setFirstName("Carlos");
        request.setSecondName("Andres");
        request.setFirstLastName("Gomez");
        request.setSecondLastName("Perez");
        request.setEmail(email);
        request.setPassword("MiPasswordSeguro123");
        return request;
    }
 
    private UserEntity buildEntity(UUID id, String email) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setFirstName("Carlos");
        entity.setFirstLastName("Gomez");
        entity.setEmail(email);
        entity.setPassword("$2a$10$hash");
        entity.setRoles(List.of(UserRole.ESTUDIANTE));
        entity.setState(UserState.ACTIVO);
        return entity;
    }

    @Test
    void create_validRequest_savesUserWithHashedPasswordAndDefaults() {
        UUID id = UUID.randomUUID();
        UserCreateRequest request = buildRequest("  Carlos.Gomez@Mail.EscuelaIng.edu.co  ");
 
        when(userRepository.existsByEmail("carlos.gomez@mail.escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode("MiPasswordSeguro123")).thenReturn("$2a$10$hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(id);
            return entity;
        });
 
        UserResponse response = userService.create(request);
 
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
 
        assertEquals("carlos.gomez@mail.escuelaing.edu.co", saved.getEmail());
        assertEquals("$2a$10$hash", saved.getPassword());
        assertNotEquals("MiPasswordSeguro123", saved.getPassword());
        assertEquals(List.of(UserRole.ESTUDIANTE), saved.getRoles());
        assertEquals(UserState.ACTIVO, saved.getState());
 
        assertEquals(id, response.getId());
        assertEquals("carlos.gomez@mail.escuelaing.edu.co", response.getEmail());
        assertEquals(List.of(UserRole.ESTUDIANTE), response.getRoles());
        assertEquals(UserState.ACTIVO, response.getState());
    }
 
    @Test
    void create_blankOptionalNames_areSavedAsNull() {
        UserCreateRequest request = buildRequest("carlos.gomez@mail.escuelaing.edu.co");
        request.setSecondName("   ");
        request.setSecondLastName("");
 
        when(userRepository.existsByEmail("carlos.gomez@mail.escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode("MiPasswordSeguro123")).thenReturn("$2a$10$hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
 
        userService.create(request);
 
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertNull(captor.getValue().getSecondName());
        assertNull(captor.getValue().getSecondLastName());
    }
 
    @Test
    void create_emailAlreadyExists_throwsAndDoesNotSave() {
        UserCreateRequest request = buildRequest("Carlos.Gomez@mail.escuelaing.edu.co");
 
        when(userRepository.existsByEmail("carlos.gomez@mail.escuelaing.edu.co")).thenReturn(true);
 
        assertThrows(EmailAlreadyExistsException.class, () -> userService.create(request));
 
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void findAll_returnsAllUsersMappedToResponses() {
        UserEntity first = buildEntity(UUID.randomUUID(), "uno@mail.escuelaing.edu.co");
        UserEntity second = buildEntity(UUID.randomUUID(), "dos@mail.escuelaing.edu.co");
        when(userRepository.findAll()).thenReturn(List.of(first, second));
 
        List<UserResponse> result = userService.findAll();
 
        assertEquals(2, result.size());
        assertEquals("uno@mail.escuelaing.edu.co", result.get(0).getEmail());
        assertEquals("dos@mail.escuelaing.edu.co", result.get(1).getEmail());
    }
 
    @Test
    void findAll_noUsers_returnsEmptyList() {
        when(userRepository.findAll()).thenReturn(List.of());
 
        List<UserResponse> result = userService.findAll();
 
        assertTrue(result.isEmpty());
    }

    @Test
    void findById_existingUser_returnsResponse() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(buildEntity(id, "carlos.gomez@mail.escuelaing.edu.co")));
 
        UserResponse result = userService.findById(id);
 
        assertEquals(id, result.getId());
        assertEquals("carlos.gomez@mail.escuelaing.edu.co", result.getEmail());
    }
 
    @Test
    void findById_unknownUser_throwsUserNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
 
        assertThrows(UserNotFoundException.class, () -> userService.findById(id));
    }
}
