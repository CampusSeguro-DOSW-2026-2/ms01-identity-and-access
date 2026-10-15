package edu.eci.dosw.login.service;

import edu.eci.dosw.login.dto.LoginRequest;
import edu.eci.dosw.login.dto.LoginResponse;
import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;
import edu.eci.dosw.login.dto.RegisterUpdateRequest;
import edu.eci.dosw.login.dto.RoleResponse;
import edu.eci.dosw.login.dto.UserPatchRequest;
import edu.eci.dosw.login.entity.UserEntity;
import edu.eci.dosw.login.entity.UserRol;
import edu.eci.dosw.login.exception.EmailAlreadyExistsException;
import edu.eci.dosw.login.exception.InvalidCredentialsException;
import edu.eci.dosw.login.exception.UserNotFoundException;
import edu.eci.dosw.login.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link AccessServiceImpl} con JUnit 5 + Mockito + AssertJ.
 */
@ExtendWith(MockitoExtension.class)
class AccessServiceTest {

    private static final String HASH = "$2a$10$hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new AccessServiceImpl(userRepository, passwordEncoder);
    }

    private UserEntity user(Long id, String email, UserRol rol) {
        UserEntity entity = new UserEntity(email, HASH, rol);
        entity.setId(id);
        return entity;
    }

    @Test
    @DisplayName("findAll retorna todos los usuarios mapeados a RegisterResponse")
    void findAll_returnsMappedUsers() {
        when(userRepository.findAll()).thenReturn(List.of(
                user(1L, "ana@uni.edu", UserRol.ESTUDIANTE),
                user(2L, "leo@uni.edu", UserRol.PROFESOR)));

        List<RegisterResponse> result = accessService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).email()).isEqualTo("ana@uni.edu");
        assertThat(result.get(1).rol()).isEqualTo(UserRol.PROFESOR);
    }

    @Test
    @DisplayName("findById retorna el usuario cuando existe")
    void findById_whenExists_returnsUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "ana@uni.edu", UserRol.ESTUDIANTE)));

        RegisterResponse response = accessService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("ana@uni.edu");
    }

    @Test
    @DisplayName("findById lanza UserNotFoundException cuando no existe")
    void findById_whenMissing_throwsNotFound() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.findById(9L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("findByEmail normaliza el correo y retorna el usuario")
    void findByEmail_normalizesEmail() {
        when(userRepository.findByEmail("ana@uni.edu"))
                .thenReturn(Optional.of(user(1L, "ana@uni.edu", UserRol.ESTUDIANTE)));

        RegisterResponse response = accessService.findByEmail("  ANA@uni.edu ");

        assertThat(response.email()).isEqualTo("ana@uni.edu");
    }

    @Test
    @DisplayName("findByEmail lanza UserNotFoundException cuando no existe")
    void findByEmail_whenMissing_throwsNotFound() {
        when(userRepository.findByEmail("x@uni.edu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.findByEmail("x@uni.edu"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("register cifra la contrasena y persiste el usuario")
    void register_whenEmailIsNew_savesUser() {
        when(userRepository.existsByEmail("ana@uni.edu")).thenReturn(false);
        when(passwordEncoder.encode("Secreta123")).thenReturn(HASH);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(5L);
            return entity;
        });

        RegisterResponse response = accessService.register(
                new RegisterRequest("ana@uni.edu", "Secreta123", UserRol.ESTUDIANTE));

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.email()).isEqualTo("ana@uni.edu");
        assertThat(response.rol()).isEqualTo(UserRol.ESTUDIANTE);
        verify(passwordEncoder).encode("Secreta123");
    }

    @Test
    @DisplayName("register rechaza correos duplicados")
    void register_whenEmailExists_throwsConflict() {
        when(userRepository.existsByEmail("ana@uni.edu")).thenReturn(true);

        assertThatThrownBy(() -> accessService.register(
                new RegisterRequest("ana@uni.edu", "Secreta123", UserRol.ESTUDIANTE)))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login retorna una sesion autenticada cuando las credenciales son validas")
    void login_withValidCredentials_returnsSession() {
        when(userRepository.findByEmail("ana@uni.edu"))
                .thenReturn(Optional.of(user(1L, "ana@uni.edu", UserRol.ESTUDIANTE)));
        when(passwordEncoder.matches("Secreta123", HASH)).thenReturn(true);

        LoginResponse response = accessService.login(new LoginRequest("ana@uni.edu", "Secreta123"));

        assertThat(response.authenticated()).isTrue();
        assertThat(response.user().email()).isEqualTo("ana@uni.edu");
        assertThat(response.accessToken()).isNull();
    }

    @Test
    @DisplayName("login falla con correo desconocido sin revelar la causa")
    void login_withUnknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmail("desconocido@uni.edu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.login(new LoginRequest("desconocido@uni.edu", "Secreta123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    @DisplayName("login falla con contrasena incorrecta")
    void login_withWrongPassword_throwsInvalidCredentials() {
        when(userRepository.findByEmail("ana@uni.edu"))
                .thenReturn(Optional.of(user(1L, "ana@uni.edu", UserRol.ESTUDIANTE)));
        when(passwordEncoder.matches("mala", HASH)).thenReturn(false);

        assertThatThrownBy(() -> accessService.login(new LoginRequest("ana@uni.edu", "mala")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("update reemplaza todos los campos del usuario")
    void update_replacesAllFields() {
        UserEntity existing = user(1L, "ana@uni.edu", UserRol.ESTUDIANTE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("nueva@uni.edu")).thenReturn(false);
        when(passwordEncoder.encode("Secreta456")).thenReturn("$2a$10$new");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = accessService.update(
                new RegisterUpdateRequest("nueva@uni.edu", "Secreta456", UserRol.PROFESOR), 1L);

        assertThat(response.email()).isEqualTo("nueva@uni.edu");
        assertThat(response.rol()).isEqualTo(UserRol.PROFESOR);
        assertThat(existing.getPassword()).isEqualTo("$2a$10$new");
    }

    @Test
    @DisplayName("update rechaza cambiar el correo a uno ya registrado")
    void update_whenEmailTaken_throwsConflict() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "ana@uni.edu", UserRol.ESTUDIANTE)));
        when(userRepository.existsByEmail("otro@uni.edu")).thenReturn(true);

        assertThatThrownBy(() -> accessService.update(
                new RegisterUpdateRequest("otro@uni.edu", "Secreta456", UserRol.PROFESOR), 1L))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    @DisplayName("patch actualiza solo los campos presentes")
    void patch_updatesOnlyProvidedFields() {
        UserEntity existing = user(1L, "ana@uni.edu", UserRol.ESTUDIANTE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = accessService.patch(new UserPatchRequest(null, null, UserRol.AUDITOR), 1L);

        assertThat(response.rol()).isEqualTo(UserRol.AUDITOR);
        assertThat(response.email()).isEqualTo("ana@uni.edu");
        assertThat(existing.getPassword()).isEqualTo(HASH);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("patch cambia el correo si es valido y nuevo")
    void patch_whenEmailIsNew_updatesEmail() {
        UserEntity existing = user(1L, "ana@uni.edu", UserRol.ESTUDIANTE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("nueva@uni.edu")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = accessService.patch(
                new UserPatchRequest("nueva@uni.edu", null, null), 1L);

        assertThat(response.email()).isEqualTo("nueva@uni.edu");
    }

    @Test
    @DisplayName("patch cifra la contrasena cuando se envia")
    void patch_whenPasswordProvided_encodesIt() {
        UserEntity existing = user(1L, "ana@uni.edu", UserRol.ESTUDIANTE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("Secreta456")).thenReturn("$2a$10$new");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        accessService.patch(new UserPatchRequest(null, "Secreta456", null), 1L);

        assertThat(existing.getPassword()).isEqualTo("$2a$10$new");
    }

    @Test
    @DisplayName("assignRol cambia el rol del usuario")
    void assignRol_changesRole() {
        UserEntity existing = user(1L, "ana@uni.edu", UserRol.ESTUDIANTE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = accessService.assignRol(1L, UserRol.BRIGADISTA);

        assertThat(response.rol()).isEqualTo(UserRol.BRIGADISTA);
    }

    @Test
    @DisplayName("delete elimina un usuario existente")
    void delete_whenExists_removesUser() {
        UserEntity existing = user(1L, "ana@uni.edu", UserRol.ESTUDIANTE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        accessService.delete(1L);

        verify(userRepository).delete(existing);
    }

    @Test
    @DisplayName("delete lanza UserNotFoundException si el usuario no existe")
    void delete_whenMissing_throwsNotFound() {
        when(userRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.delete(3L))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("findRoles retorna el catalogo completo de roles")
    void findRoles_returnsAllRoles() {
        List<RoleResponse> roles = accessService.findRoles();

        assertThat(roles).hasSize(UserRol.values().length);
        assertThat(roles).extracting(RoleResponse::code).contains("ESTUDIANTE", "AUDITOR");
    }
}