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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IssuedToken;
import edu.eci.dosw.campusseguro.identity.identity_service.security.JwtService;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.AuthServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleServiceImpl;

/**
 * RF-MS01-05 postcondicion / RN-MS01-09 / MR-06: los nuevos roles se reflejan en el siguiente token. Se
 * cambian los roles de un usuario con RoleServiceImpl y luego se hace login con AuthServiceImpl sobre el
 * mismo usuario: el JWT se emite con los roles nuevos.
 */
@ExtendWith(MockitoExtension.class)
class RoleChangeNextLoginTest {

    private static final Instant NOW = Instant.parse("2026-10-23T15:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c");
    private static final String EMAIL = "maria.perez@mail.escuelaing.edu.co";
    private static final String PASSWORD = "Campus#2026";
    private static final String STORED_HASH = "$2a$10$stored.hash.value";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditEventPublisher auditPublisher;

    private RoleServiceImpl roleService;
    private AuthServiceImpl authService;

    /**
     * Crea ambos servicios sobre los mismos mocks y el mismo reloj fijo.
     */
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        roleService = new RoleServiceImpl(userRepository, auditPublisher, clock);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, auditPublisher, clock);
    }

    /**
     * Tras agregar el rol BRIGADISTA, el siguiente login emite el token con ESTUDIANTE y BRIGADISTA.
     */
    @Test
    void nextLogin_afterAddingARole_issuesTheTokenWithTheNewRoles() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setPassword(STORED_HASH);
        user.setState(UserState.ACTIVO);
        user.setRoles(EnumSet.of(UserRole.ESTUDIANTE));
        when(userRepository.findAllByIdInOrderById(any())).thenReturn(List.of(user));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtService.issueToken(eq(USER_ID), eq(EMAIL), any()))
                .thenReturn(new IssuedToken("header.payload.signature", NOW.plusSeconds(3600)));

        ChangeRolesRequest change = new ChangeRolesRequest();
        change.setIds(List.of(USER_ID.toString()));
        change.setAgregar(List.of("BRIGADISTA"));
        roleService.changeRoles(new RequesterIdentity(ADMIN_ID, EnumSet.of(UserRole.ADMIN)), change, "corr-1");

        LoginRequest login = new LoginRequest();
        login.setCorreo(EMAIL);
        login.setContrasena(PASSWORD);
        authService.login(login, "corr-2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserRole>> roles = ArgumentCaptor.forClass(List.class);
        verify(jwtService).issueToken(eq(USER_ID), eq(EMAIL), roles.capture());
        assertEquals(List.of(UserRole.BRIGADISTA, UserRole.ESTUDIANTE), roles.getValue());
    }
}
