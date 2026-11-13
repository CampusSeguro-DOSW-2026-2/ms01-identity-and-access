package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * Pruebas de la búsqueda y listado de usuarios (RF-MS01-07, formulario F-07)
 */
class UserSearchServiceTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private UserServiceImpl userService;

    private final RequesterIdentity admin = identity("ADMIN");
    private final RequesterIdentity auditor = identity("AUDITOR");
    private final RequesterIdentity student = identity("ESTUDIANTE");

    /**
     * Construye la identidad de un solicitante con un rol
     *
     * @param role rol del solicitante
     * @return la identidad del solicitante
     */
    private static RequesterIdentity identity(String role) {
        return TestIdentities.of(UUID.randomUUID(), role);
    }

    /**
     * Prepara el servicio con sus dependencias simuladas
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        when(roleRepository.existsById("ESTUDIANTE")).thenReturn(true);
        when(roleRepository.existsById("MONITOR_EVACUACION")).thenReturn(true);
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        AuditRecorder auditRecorder = new AuditRecorder(mock(AuditEventPublisher.class), clock);
        RequestValidator requestValidator = new RequestValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
        userService = new UserServiceImpl(userRepository, roleRepository, mock(PasswordEncoder.class), auditRecorder,
                requestValidator, FixedDatabaseTime.at(Instant.parse("2026-03-01T10:00:00Z")), List.of("escuelaing.edu.co"));
    }

    /**
     * Construye un usuario de prueba
     *
     * @param email correo del usuario
     * @param state estado del usuario
     * @param roles roles del usuario
     * @return el usuario de prueba
     */
    private UserEntity user(String email, UserState state, String... roles) {
        UserEntity u = new UserEntity();
        u.setId(UUID.randomUUID());
        u.setFirstName("Carlos");
        u.setFirstLastName("Gomez");
        u.setEmail(email);
        u.setPassword("$2a$10$hash");
        u.setRoles(new HashSet<>(List.of(roles)));
        u.setState(state);
        return u;
    }

    /**
     * Configura el repositorio para devolver una página vacía
     */
    private void stubEmptyPage() {
        when(userRepository.searchUsers(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    /**
     * La búsqueda devuelve la página con los perfiles completos y los datos de paginación
     */
    @Test
    void search_asAdmin_returnsPageWithFullProfilesAndPaginationData() {
        UserEntity a = user("ana@escuelaing.edu.co", UserState.ACTIVO, "ESTUDIANTE");
        UserEntity b = user("beto@escuelaing.edu.co", UserState.INACTIVO, "PROFESOR",
                "BRIGADISTA");
        when(userRepository.searchUsers(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(1, 2), 5));

        PageResponse<UserProfileResponse> result = userService.search(admin, "an", "ACTIVO", "ESTUDIANTE", "1",
                "2");

        assertEquals(2, result.contenido().size());
        assertEquals(5, result.totalElementos());
        assertEquals(3, result.totalPaginas());
        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(UserState.INACTIVO, result.contenido().get(1).state());
        assertEquals(List.of("BRIGADISTA", "PROFESOR"), result.contenido().get(1).roles());
        assertEquals("Carlos", result.contenido().get(0).firstName());
    }

    /**
     * La búsqueda entrega los filtros y ordena por fecha de creación descendente y luego por id
     */
    @Test
    void search_passesFiltersAndSortsByCreationDateDescendingThenId() {
        stubEmptyPage();

        userService.search(admin, "  Ana  ", "ACTIVO", "ESTUDIANTE", "1", "2");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchUsers(eq("ana"), eq(UserState.ACTIVO), eq("ESTUDIANTE"),
                captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(2, pageable.getPageSize());
        assertTrue(pageable.getSort().getOrderFor("createdAt").isDescending());
        assertTrue(pageable.getSort().getOrderFor("id").isAscending());
    }

    /**
     * Sin parámetros se usan la página y el tamaño por defecto
     */
    @Test
    void search_withoutParameters_usesDefaultPageAndSize() {
        stubEmptyPage();

        userService.search(auditor, null, null, null, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchUsers(any(), any(), any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }

    /**
     * El valor Todos y los filtros en blanco significan sin filtro
     */
    @Test
    void search_todosAndBlankFilters_meanNoFilter() {
        stubEmptyPage();

        assertDoesNotThrow(() -> userService.search(admin, "   ", "Todos", "todos", "0", "20"));

        verify(userRepository).searchUsers(eq(null), eq(null), eq(null), any(Pageable.class));
    }

    /**
     * Un solicitante sin permiso de lectura se prohíbe y no se consulta la base de datos
     */
    @Test
    void search_requesterWithoutReadPermission_isForbiddenAndDoesNotQuery() {
        assertThrows(ForbiddenOperationException.class,
                () -> userService.search(student, null, null, null, null, null));

        verify(userRepository, never()).searchUsers(any(), any(), any(), any(Pageable.class));
    }

    /**
     * El permiso se verifica antes de validar los parámetros
     */
    @Test
    void search_permissionIsCheckedBeforeValidatingParameters() {
        assertThrows(ForbiddenOperationException.class,
                () -> userService.search(student, null, "XYZ", "SUPER-MAN", "-1", "1000"));
    }

    /**
     * Los filtros inválidos informan un error por campo con los mensajes del formulario
     */
    @Test
    void search_invalidFilters_reportOneErrorPerFieldWithTheFormMessages() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.search(admin, "a<b", "XYZ", "SUPER-MAN", "-1", "101"));

        assertEquals(5, error.getErrors().size());
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("correo", "Búsqueda no válida.")));
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("estado", "Estado no válido.")));
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("rol", "Rol no válido.")));
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("page", "Página no válida.")));
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("size",
                "Tamaño de página no válido (1 a 100).")));
        verify(userRepository, never()).searchUsers(any(), any(), any(), any(Pageable.class));
    }

    /**
     * El rol del filtro debe llegar con el formato guardado: en minúsculas o con símbolos se rechaza sin
     * consultar la tabla de roles
     */
    @Test
    void search_roleFilterWithoutTheStoredFormat_isRejectedWithoutQueryingRoles() {
        InvalidRequestException lowercase = assertThrows(InvalidRequestException.class,
                () -> userService.search(admin, null, null, "estudiante", null, null));
        InvalidRequestException symbols = assertThrows(InvalidRequestException.class,
                () -> userService.search(admin, null, null, "ROL-1", null, null));

        assertEquals("Rol no válido.", lowercase.getErrors().get(0).mensaje());
        assertEquals("Rol no válido.", symbols.getErrors().get(0).mensaje());
        verify(roleRepository, never()).existsById(any());
    }

    /**
     * Un rol con formato válido que no existe en la tabla de roles se rechaza y no se buscan usuarios
     */
    @Test
    void search_roleFilterThatDoesNotExist_isRejected() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.search(admin, null, null, "FANTASMA", null, null));

        assertEquals("Datos inválidos.", error.getMessage());
        assertEquals(new ErrorResponse.ErrorDetail("rol", "Rol no válido."), error.getErrors().get(0));
        verify(roleRepository).existsById("FANTASMA");
        verify(userRepository, never()).searchUsers(any(), any(), any(), any(Pageable.class));
    }

    /**
     * Un rol personalizado que existe sirve de filtro
     */
    @Test
    void search_roleFilterWithACustomRole_isAccepted() {
        stubEmptyPage();

        userService.search(admin, null, null, "MONITOR_EVACUACION", null, null);

        verify(userRepository).searchUsers(eq(null), eq(null), eq("MONITOR_EVACUACION"), any(Pageable.class));
    }

    /**
     * Sin filtro de rol no se consulta la tabla de roles
     */
    @Test
    void search_withoutRoleFilter_doesNotQueryTheRolesTable() {
        stubEmptyPage();

        userService.search(admin, null, null, "Todos", null, null);

        verify(roleRepository, never()).existsById(any());
    }

    /**
     * Una página o un tamaño que no son números se rechazan
     */
    @Test
    void search_nonNumericPageOrSize_areRejected() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.search(admin, null, null, null, "abc", "xyz"));

        assertEquals(2, error.getErrors().size());
    }

    /**
     * Los límites del tamaño de página son inclusivos
     */
    @Test
    void search_sizeBoundaries_areInclusive() {
        stubEmptyPage();

        assertDoesNotThrow(() -> userService.search(admin, null, null, null, "0", "1"));
        assertDoesNotThrow(() -> userService.search(admin, null, null, null, "0", "100"));
        assertThrows(InvalidRequestException.class, () -> userService.search(admin, null, null, null, "0", "0"));
        assertThrows(InvalidRequestException.class,
                () -> userService.search(admin, null, null, null, "0", "101"));
    }

    /**
     * Un filtro de correo de más de 100 caracteres se rechaza
     */
    @Test
    void search_emailLongerThan100Characters_isRejected() {
        String longEmail = "a".repeat(101);

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.search(admin, longEmail, null, null, null, null));

        assertEquals("correo", error.getErrors().get(0).campo());
    }

    /**
     * Un resultado vacío devuelve una página vacía y no un error
     */
    @Test
    void search_emptyResult_returnsEmptyPageNotAnError() {
        stubEmptyPage();

        PageResponse<UserProfileResponse> result = userService.search(admin, "nadie", null, null, null, null);

        assertTrue(result.contenido().isEmpty());
        assertEquals(0, result.totalElementos());
    }
}
