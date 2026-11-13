package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IssuedToken;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtService;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleServiceImpl;

/**
 * RF-MS01-05 postcondicion / RN-MS01-09 / MR-06: los nuevos roles se reflejan en el siguiente token. Se
 * cambian los roles de un usuario con RoleServiceImpl y luego se hace login con AuthServiceImpl sobre el
 * mismo usuario: el JWT se emite con los roles nuevos
 */
@ExtendWith(MockitoExtension.class)
class RoleChangeNextLoginTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final String EMAIL = "maria.perez@escuelaing.edu.co";
    private static final String PASSWORD = "Campus#2026";
    private static final String STORED_HASH = "$2a$10$stored.hash.value";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditEventPublisher auditPublisher;

    private RoleServiceImpl roleService;
    private AuthServiceImpl authService;

    /**
     * Crea ambos servicios sobre los mismos mocks y el mismo reloj fijo
     */
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        AuditRecorder recorder = new AuditRecorder(auditPublisher, clock);
        roleService = new RoleServiceImpl(userRepository, roleRepository, recorder, FixedDatabaseTime.at(NOW));
        authService = new AuthServiceImpl(userRepository, roleRepository, passwordEncoder, jwtService, recorder,
                new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator()));
    }

    /**
     * Tras agregar el rol BRIGADISTA, el siguiente login emite el token con ESTUDIANTE y BRIGADISTA
     */
    @Test
    void nextLogin_afterAddingARole_issuesTheTokenWithTheNewRoles() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setPassword(STORED_HASH);
        user.setState(UserState.ACTIVO);
        user.setRoles(Set.of("ESTUDIANTE"));
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(user));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(roleRepository.countByNameIn(any())).thenReturn(1L);
        when(roleRepository.findAllByNameIn(List.of("BRIGADISTA", "ESTUDIANTE"))).thenReturn(List.of(
                role("ESTUDIANTE", "ms01:profile:read"), role("BRIGADISTA", "ms05:panel:read_own")));
        when(jwtService.issueToken(eq(USER_ID), eq(EMAIL), any(), any()))
                .thenReturn(new IssuedToken("header.payload.signature", NOW.plusSeconds(3600)));

        ChangeRolesRequest change = new ChangeRolesRequest();
        change.setIds(List.of(USER_ID.toString()));
        change.setAgregar(List.of("BRIGADISTA"));
        roleService.changeRoles(TestIdentities.of(ADMIN_ID, "ADMIN"), change,
                "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90");

        LoginRequest login = new LoginRequest();
        login.setCorreo(EMAIL);
        login.setContrasena(PASSWORD);
        authService.login(login, "1c2f9d34-5b7a-4e0c-9d61-7a4b8e3f2a10");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> roles = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> permissions = ArgumentCaptor.forClass(List.class);
        verify(jwtService).issueToken(eq(USER_ID), eq(EMAIL), roles.capture(), permissions.capture());
        assertEquals(List.of("BRIGADISTA", "ESTUDIANTE"), roles.getValue());
        assertEquals(List.of("ms01:profile:read", "ms05:panel:read_own"), permissions.getValue());
    }

    /**
     * Construye un rol guardado con los permisos indicados
     *
     * @param name nombre del rol
     * @param permissions códigos de permiso del rol
     * @return el rol de prueba
     */
    private RoleEntity role(String name, String... permissions) {
        RoleEntity entity = new RoleEntity();
        entity.setName(name);
        entity.setDescription("Rol " + name);
        entity.setPermissions(List.of(permissions));
        return entity;
    }
}
