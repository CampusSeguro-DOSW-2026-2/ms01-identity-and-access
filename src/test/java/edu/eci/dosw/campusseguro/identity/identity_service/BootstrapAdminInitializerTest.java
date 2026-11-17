package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.config.BootstrapAdminInitializer;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;

/**
 * RN-MS01-12 / "Initial data" section of the data model: the first administrator is created at startup only if no ADMIN
 * exists, with the email and the password of the environment variables BOOTSTRAP_ADMIN_EMAIL and
 * BOOTSTRAP_ADMIN_PASSWORD, and it is left ACTIVO with the role ADMIN
 */
@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    private static final String EMAIL = "  Admin.Inicial@Mail.EscuelaIng.edu.co ";
    private static final String NORMALIZED_EMAIL = "admin.inicial@mail.escuelaing.edu.co";
    private static final String PASSWORD = "Campus#Admin2026";
    private static final String HASH = "$2a$10$hash.del.administrador";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments arguments;

    private BootstrapAdminInitializer initializer;

        /**
     * Creates the initializer with the complete configuration and default names
     */
    @BeforeEach
    void setUp() {
        initializer = initializer(EMAIL, PASSWORD, null, null);
    }

        /**
     * Creates an initializer with the given configuration values
     *
     * @param email configured email
     * @param password configured password
     * @param firstName configured first name
     * @param lastName configured first lastname
     * @return the initializer
     */
    private BootstrapAdminInitializer initializer(String email, String password, String firstName,
            String lastName) {
        return new BootstrapAdminInitializer(userRepository, roleRepository, passwordEncoder, email, password,
                firstName, lastName);
    }

        /**
     * Captures the user that was saved
     *
     * @return the saved administrator
     */
    private UserEntity savedAdmin() {
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

        /**
     * With no ADMIN and with the complete configuration the first administrator is created: ACTIVO, with role ADMIN,
     * normalized email and password stored as a hash
     */
    @Test
    void create_noAdminExists_createsTheFirstAdmin() {
        when(userRepository.countWithRole("ADMIN")).thenReturn(0L);
        when(userRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(false);
        RoleEntity adminRole = TestEntities.role("ADMIN", "ms01:profile:read", "ms01:role:manage");
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

        boolean created = initializer.createFirstAdminIfNeeded();

        assertTrue(created);
        UserEntity admin = savedAdmin();
        assertEquals(NORMALIZED_EMAIL, admin.getEmail());
        assertEquals(Set.of(adminRole), admin.getRoles());
        assertEquals(UserState.ACTIVO, admin.getState());
        assertEquals(HASH, admin.getPassword());
        assertNotEquals(PASSWORD, admin.getPassword());
        assertEquals("Administrador", admin.getFirstName());
        assertEquals("Inicial", admin.getFirstLastName());
    }

        /**
     * The configured names are used, trimmed
     */
    @Test
    void create_withConfiguredNames_usesThem() {
        initializer = initializer(EMAIL, PASSWORD, "  Laura ", " Gomez ");
        when(userRepository.countWithRole("ADMIN")).thenReturn(0L);
        when(userRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(TestEntities.role("ADMIN", "ms01:profile:read")));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

        initializer.createFirstAdminIfNeeded();

        UserEntity admin = savedAdmin();
        assertEquals("Laura", admin.getFirstName());
        assertEquals("Gomez", admin.getFirstLastName());
    }

        /**
     * If an ADMIN already exists another one is not created
     */
    @Test
    void create_adminAlreadyExists_doesNothing() {
        when(userRepository.countWithRole("ADMIN")).thenReturn(1L);

        boolean created = initializer.createFirstAdminIfNeeded();

        assertFalse(created);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

        /**
     * Without a configured email no administrator is created and the database is not queried
     */
    @Test
    void create_withoutEmail_doesNothing() {
        assertFalse(initializer(null, PASSWORD, null, null).createFirstAdminIfNeeded());
        assertFalse(initializer("   ", PASSWORD, null, null).createFirstAdminIfNeeded());

        verifyNoInteractions(userRepository, passwordEncoder);
    }

        /**
     * Without a configured password no administrator is created and the database is not queried
     */
    @Test
    void create_withoutPassword_doesNothing() {
        assertFalse(initializer(EMAIL, null, null, null).createFirstAdminIfNeeded());
        assertFalse(initializer(EMAIL, "", null, null).createFirstAdminIfNeeded());

        verifyNoInteractions(userRepository, passwordEncoder);
    }

        /**
     * If the email already belongs to a user without the ADMIN role it is neither created nor promoted automatically
     */
    @Test
    void create_emailBelongsToAnotherUser_doesNotPromoteIt() {
        when(userRepository.countWithRole("ADMIN")).thenReturn(0L);
        when(userRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(true);

        boolean created = initializer.createFirstAdminIfNeeded();

        assertFalse(created);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

        /**
     * If the ADMIN system role does not exist in the database the administrator is not created and the startup fails
     */
    @Test
    void create_adminRoleMissingInTheDatabase_failsWithoutSavingAnything() {
        when(userRepository.countWithRole("ADMIN")).thenReturn(0L);
        when(userRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> initializer.createFirstAdminIfNeeded());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

        /**
     * When the application starts (ApplicationRunner) the creation of the first administrator is executed
     */
    @Test
    void run_executesTheCreation() {
        when(userRepository.countWithRole("ADMIN")).thenReturn(0L);
        when(userRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(TestEntities.role("ADMIN", "ms01:profile:read")));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

        initializer.run(arguments);

        assertEquals(NORMALIZED_EMAIL, savedAdmin().getEmail());
    }
}
