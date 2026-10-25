package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;

/**
 * RN-MS01-12 / seccion "Datos iniciales" del modelo de datos: el primer administrador se crea al arrancar solo
 * si no existe ningun ADMIN, con el correo y la contrasena de las variables de entorno BOOTSTRAP_ADMIN_EMAIL
 * y BOOTSTRAP_ADMIN_PASSWORD, y queda ACTIVO con el rol ADMIN.
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
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments arguments;

    private BootstrapAdminInitializer initializer;

    /**
     * Crea el inicializador con la configuracion completa y nombres por defecto.
     */
    @BeforeEach
    void setUp() {
        initializer = initializer(EMAIL, PASSWORD, null, null);
    }

    /**
     * Crea un inicializador con los valores de configuracion indicados.
     *
     * @param email correo configurado
     * @param password contrasena configurada
     * @param firstName primer nombre configurado
     * @param lastName primer apellido configurado
     * @return el inicializador
     */
    private BootstrapAdminInitializer initializer(String email, String password, String firstName,
            String lastName) {
        return new BootstrapAdminInitializer(userRepository, passwordEncoder, email, password, firstName,
                lastName);
    }

    /**
     * Captura el usuario que se guardo.
     *
     * @return el administrador guardado
     */
    private UserEntity savedAdmin() {
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    /**
     * Sin ningun ADMIN y con la configuracion completa se crea el primer administrador: ACTIVO, con rol
     * ADMIN, correo normalizado y contrasena guardada como hash.
     */
    @Test
    void create_noAdminExists_createsTheFirstAdmin() {
        when(userRepository.countWithRole(UserRole.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

        boolean created = initializer.createFirstAdminIfNeeded();

        assertTrue(created);
        UserEntity admin = savedAdmin();
        assertEquals(NORMALIZED_EMAIL, admin.getEmail());
        assertEquals(Set.of(UserRole.ADMIN), admin.getRoles());
        assertEquals(UserState.ACTIVO, admin.getState());
        assertEquals(HASH, admin.getPassword());
        assertNotEquals(PASSWORD, admin.getPassword());
        assertEquals("Administrador", admin.getFirstName());
        assertEquals("Inicial", admin.getFirstLastName());
    }

    /**
     * Los nombres configurados se usan, recortados.
     */
    @Test
    void create_withConfiguredNames_usesThem() {
        initializer = initializer(EMAIL, PASSWORD, "  Laura ", " Gomez ");
        when(userRepository.countWithRole(UserRole.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

        initializer.createFirstAdminIfNeeded();

        UserEntity admin = savedAdmin();
        assertEquals("Laura", admin.getFirstName());
        assertEquals("Gomez", admin.getFirstLastName());
    }

    /**
     * Si ya existe un ADMIN no se crea otro.
     */
    @Test
    void create_adminAlreadyExists_doesNothing() {
        when(userRepository.countWithRole(UserRole.ADMIN)).thenReturn(1L);

        boolean created = initializer.createFirstAdminIfNeeded();

        assertFalse(created);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    /**
     * Sin correo configurado no se crea ningun administrador ni se consulta la base de datos.
     */
    @Test
    void create_withoutEmail_doesNothing() {
        assertFalse(initializer(null, PASSWORD, null, null).createFirstAdminIfNeeded());
        assertFalse(initializer("   ", PASSWORD, null, null).createFirstAdminIfNeeded());

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    /**
     * Sin contrasena configurada no se crea ningun administrador ni se consulta la base de datos.
     */
    @Test
    void create_withoutPassword_doesNothing() {
        assertFalse(initializer(EMAIL, null, null, null).createFirstAdminIfNeeded());
        assertFalse(initializer(EMAIL, "", null, null).createFirstAdminIfNeeded());

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    /**
     * Si el correo ya pertenece a un usuario sin rol ADMIN no se crea ni se asciende automaticamente.
     */
    @Test
    void create_emailBelongsToAnotherUser_doesNotPromoteIt() {
        when(userRepository.countWithRole(UserRole.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(true);

        boolean created = initializer.createFirstAdminIfNeeded();

        assertFalse(created);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    /**
     * Al arrancar la aplicacion (ApplicationRunner) se ejecuta la creacion del primer administrador.
     */
    @Test
    void run_executesTheCreation() {
        when(userRepository.countWithRole(UserRole.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

        initializer.run(arguments);

        assertEquals(NORMALIZED_EMAIL, savedAdmin().getEmail());
    }
}
