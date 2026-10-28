package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * Pruebas del registro (RF-MS01-01), el perfil propio (RF-MS01-06) y la consulta por id (RF-MS01-07)
 */
class UserServiceTest {

    private static final List<String> DOMAINS = List.of("escuelaing.edu.co", "mail.escuelaing.edu.co");
    private static final String PASSWORD = "MiPassword#2026";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuditEventPublisher auditPublisher;
    private UserServiceImpl userService;

    /**
     * Prepara el servicio con sus dependencias simuladas
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditPublisher = mock(AuditEventPublisher.class);
        userService = serviceWithDomains(DOMAINS);
    }

    /**
     * Construye el servicio con las dependencias simuladas y los dominios institucionales indicados
     *
     * @param domains dominios institucionales con los que se configura el servicio
     * @return el servicio configurado
     */
    private UserServiceImpl serviceWithDomains(List<String> domains) {
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        AuditRecorder auditRecorder = new AuditRecorder(auditPublisher, clock);
        RequestValidator requestValidator = new RequestValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
        return new UserServiceImpl(userRepository, passwordEncoder, auditRecorder, requestValidator, domains);
    }

    /**
     * Construye una petición de registro válida
     *
     * @param email correo de la petición
     * @return la petición de registro
     */
    private UserCreateRequest registrationRequest(String email) {
        UserCreateRequest request = new UserCreateRequest();
        request.setFirstName("Carlos");
        request.setSecondName("Andres");
        request.setFirstLastName("Gomez");
        request.setSecondLastName("Perez");
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setPasswordConfirmation(PASSWORD);
        return request;
    }

    /**
     * Construye un usuario guardado de ejemplo
     *
     * @param id identificador del usuario
     * @param email correo del usuario
     * @return el usuario de ejemplo
     */
    private UserEntity entity(UUID id, String email) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setFirstName("Carlos");
        entity.setFirstLastName("Gomez");
        entity.setEmail(email);
        entity.setPassword("$2a$10$hash");
        entity.setRoles(EnumSet.of(UserRole.ESTUDIANTE));
        entity.setState(UserState.ACTIVO);
        return entity;
    }

    /**
     * Construye la identidad de un solicitante
     *
     * @param id identificador del solicitante
     * @param roles roles del solicitante
     * @return la identidad del solicitante
     */
    private RequesterIdentity identity(UUID id, UserRole... roles) {
        return new RequesterIdentity(id, EnumSet.copyOf(List.of(roles)));
    }

    /**
     * Obtiene el único evento de auditoría que se publicó
     *
     * @return el evento de auditoría publicado
     */
    private AuditEvent singleAuditEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(captor.capture());
        return captor.getValue();
    }

    /**
     * Un registro válido guarda al usuario con la contraseña cifrada y los valores por defecto
     */
    @Test
    void create_validRequest_savesUserWithHashedPasswordAndDefaults() {
        UUID id = UUID.randomUUID();
        UserCreateRequest request = registrationRequest("  Carlos.Gomez@EscuelaIng.edu.co  ");

        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        UserResponse response = userService.create(request, CORRELATION_ID);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserEntity saved = captor.getValue();

        assertEquals("carlos.gomez@escuelaing.edu.co", saved.getEmail());
        assertEquals("$2a$10$hash", saved.getPassword());
        assertNotEquals(PASSWORD, saved.getPassword());
        assertEquals(Set.of(UserRole.ESTUDIANTE), saved.getRoles());
        assertEquals(UserState.ACTIVO, saved.getState());

        assertEquals(id, response.id());
        assertEquals("carlos.gomez@escuelaing.edu.co", response.email());
        assertEquals(List.of(UserRole.ESTUDIANTE), response.roles());
        assertEquals(UserState.ACTIVO, response.state());
    }

    /**
     * Un registro exitoso se audita sin datos personales
     */
    @Test
    void create_success_auditsRegistroWithoutPersonalData() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        userService.create(registrationRequest("carlos.gomez@escuelaing.edu.co"), CORRELATION_ID);

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals("usuario:" + id, event.recursoAfectado());
        assertEquals(CORRELATION_ID, event.correlationId());
        assertNull(event.usuarioId());
        assertNull(event.usuarioRoles());
        assertNull(event.detalle());
    }

    /**
     * Los nombres opcionales en blanco se guardan como nulos
     */
    @Test
    void create_blankOptionalNames_areSavedAsNull() {
        UserCreateRequest request = registrationRequest("carlos.gomez@escuelaing.edu.co");
        request.setSecondName("   ");
        request.setSecondLastName("");

        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request, CORRELATION_ID);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getSecondName());
        assertNull(captor.getValue().getSecondLastName());
    }

    /**
     * Un correo ya registrado lanza el conflicto, se audita y no se guarda
     */
    @Test
    void create_emailAlreadyExists_throwsAuditsAndDoesNotSave() {
        UserCreateRequest request = registrationRequest("Carlos.Gomez@escuelaing.edu.co");
        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> userService.create(request, CORRELATION_ID));

        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        verify(passwordEncoder, never()).encode(any());
        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CONFLICTO", event.detalle());
        assertNull(event.recursoAfectado());
    }

    /**
     * Una violación de la restricción de unicidad se informa como conflicto de correo
     */
    @Test
    void create_uniqueConstraintViolation_isReportedAsEmailConflict() {
        UserCreateRequest request = registrationRequest("carlos.gomez@escuelaing.edu.co");
        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_correo"));

        assertThrows(EmailAlreadyExistsException.class, () -> userService.create(request, CORRELATION_ID));

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("CONFLICTO", event.detalle());
    }

    /**
     * Un dominio ajeno a la institución se rechaza con el mensaje genérico de correo
     */
    @Test
    void create_foreignDomain_isRejectedWithGenericEmailMessage() {
        UserCreateRequest request = registrationRequest("carlos.gomez@gmail.com");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.create(request, CORRELATION_ID));

        assertEquals(1, error.getErrors().size());
        assertEquals("correo", error.getErrors().get(0).campo());
        assertEquals("Correo no válido.", error.getErrors().get(0).mensaje());
        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    /**
     * El correo de cada dominio institucional configurado se acepta
     *
     * @param email correo con el dominio institucional que se prueba
     */
    @ParameterizedTest
    @ValueSource(strings = { "carlos.gomez@escuelaing.edu.co", "carlos.gomez@mail.escuelaing.edu.co" })
    void create_emailOfEachConfiguredDomain_isAccepted(String email) {
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        UserResponse response = userService.create(registrationRequest(email), CORRELATION_ID);

        assertEquals(email, response.email());
        assertEquals(id, response.id());
    }

    /**
     * Un dominio que no es igual a alguno de los configurados se rechaza, incluidos los subdominios
     * y los dominios que solo contienen uno de los configurados
     *
     * @param email correo con un dominio no permitido
     */
    @ParameterizedTest
    @ValueSource(strings = { "carlos.gomez@otro.escuelaing.edu.co", "carlos.gomez@mail.mail.escuelaing.edu.co",
            "carlos.gomez@escuelaing.edu.co.ejemplo.com", "carlos.gomez@escuelaing.edu", "carlos.gomez@gmail.com" })
    void create_domainNotInTheConfiguredList_isRejected(String email) {
        UserCreateRequest request = registrationRequest(email);

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.create(request, CORRELATION_ID));

        assertEquals("correo", error.getErrors().get(0).campo());
        assertEquals("Correo no válido.", error.getErrors().get(0).mensaje());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    /**
     * Los dominios configurados se normalizan: se ignoran los espacios, las mayúsculas y los valores vacíos
     */
    @Test
    void create_configuredDomainsAreNormalized() {
        UserServiceImpl service = serviceWithDomains(List.of(" Mail.EscuelaIng.edu.co ", "", "   "));
        UserCreateRequest rejected = registrationRequest("carlos.gomez@escuelaing.edu.co");
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail("carlos.gomez@mail.escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });

        UserResponse accepted = service.create(registrationRequest("carlos.gomez@mail.escuelaing.edu.co"),
                CORRELATION_ID);

        assertEquals("carlos.gomez@mail.escuelaing.edu.co", accepted.email());
        assertThrows(InvalidRequestException.class, () -> service.create(rejected, CORRELATION_ID));
    }

    /**
     * Datos inválidos se auditan como fallo y se informan todos los campos
     */
    @Test
    void create_invalidData_auditsFalloAndReportsEveryField() {
        UserCreateRequest request = registrationRequest("no-es-un-correo");
        request.setFirstName("A");
        request.setPasswordConfirmation("Otra#Clave2026");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.create(request, CORRELATION_ID));

        List<String> fields = error.getErrors().stream()
                .map(ErrorResponse.ErrorDetail::campo)
                .toList();
        assertTrue(fields.contains("correo"));
        assertTrue(fields.contains("primer_nombre"));
        assertTrue(fields.contains("confirmar_contrasena"));
        assertEquals(fields.stream().distinct().count(), fields.size());

        AuditEvent event = singleAuditEvent();
        assertEquals(AuditAction.REGISTRO, event.accion());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals("VALIDACION", event.detalle());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    /**
     * Un fallo de la auditoría no cambia la respuesta del registro
     */
    @Test
    void create_auditFailure_doesNotChangeTheResponse() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsByEmail("carlos.gomez@escuelaing.edu.co")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$hash");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });
        doThrow(new IllegalStateException("MS08 caído")).when(auditPublisher).publish(any(AuditEvent.class));

        UserResponse response = userService.create(registrationRequest("carlos.gomez@escuelaing.edu.co"),
                CORRELATION_ID);

        assertEquals(id, response.id());
    }

    /**
     * La consulta del propio perfil devuelve el perfil del usuario
     */
    @Test
    void getMyProfile_existingUser_returnsProfile() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(entity(id, "carlos.gomez@escuelaing.edu.co")));

        UserProfileResponse result = userService.getMyProfile(identity(id, UserRole.ESTUDIANTE));

        assertEquals("carlos.gomez@escuelaing.edu.co", result.email());
        assertEquals("Carlos", result.firstName());
    }

    /**
     * La consulta del propio perfil de un usuario inexistente lanza no encontrado
     */
    @Test
    void getMyProfile_unknownUser_throwsUserNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.getMyProfile(identity(id, UserRole.ESTUDIANTE)));
    }

    /**
     * Una actualización válida modifica y devuelve el perfil
     */
    @Test
    void updateProfile_validRequest_updatesAndReturnsProfile() {
        UUID id = UUID.randomUUID();
        UserEntity existing = entity(id, "carlos.gomez@escuelaing.edu.co");
        UserUpdateProfileRequest request = new UserUpdateProfileRequest();
        request.setFirstName("Sebastian");
        request.setFirstLastName("Gomez");

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse result = userService.updateProfile(identity(id, UserRole.ESTUDIANTE), request);

        assertEquals("Sebastian", result.firstName());
        assertEquals("carlos.gomez@escuelaing.edu.co", result.email());
        verify(userRepository).saveAndFlush(any(UserEntity.class));
    }

    /**
     * Nombres inválidos lanzan petición inválida y no se guarda
     */
    @Test
    void updateProfile_invalidNames_throwsInvalidRequestAndDoesNotSave() {
        UUID id = UUID.randomUUID();
        UserUpdateProfileRequest request = new UserUpdateProfileRequest();
        request.setFirstName("1");
        request.setFirstLastName("Gomez");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.updateProfile(identity(id, UserRole.ESTUDIANTE), request));

        assertEquals("primer_nombre", error.getErrors().get(0).campo());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    /**
     * Los campos rechazados informan los mensajes del formulario
     */
    @Test
    void updateProfile_rejectedFields_reportTheFormMessages() {
        UUID id = UUID.randomUUID();
        UserUpdateProfileRequest request = new UserUpdateProfileRequest();
        request.setFirstName("Sebastian");
        request.setFirstLastName("Gomez");
        request.registerUnknownField("correo", "otro@escuelaing.edu.co");
        request.registerUnknownField("roles", List.of("ADMIN"));
        request.registerUnknownField("estado", "INACTIVO");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.updateProfile(identity(id, UserRole.ESTUDIANTE), request));

        assertEquals(3, error.getErrors().size());
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("correo",
                "El correo no se puede modificar.")));
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("roles",
                "Los roles no se pueden modificar desde el perfil.")));
        assertTrue(error.getErrors().contains(new ErrorResponse.ErrorDetail("estado",
                "El estado no se puede modificar desde el perfil.")));
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

    /**
     * La consulta de un usuario por id con permiso devuelve su perfil
     */
    @Test
    void getUserById_withReadPermission_returnsProfile() {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(entity(targetId, "carlos.gomez@escuelaing.edu.co")));

        UserProfileResponse result = userService.getUserById(identity(requesterId, UserRole.AUDITOR), targetId);

        assertEquals(targetId, result.id());
        assertEquals("carlos.gomez@escuelaing.edu.co", result.email());
    }

    /**
     * La consulta de un usuario inexistente lanza no encontrado
     */
    @Test
    void getUserById_unknownUser_throwsUserNotFoundException() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.getUserById(identity(UUID.randomUUID(), UserRole.ADMIN), targetId));
    }

    /**
     * Sin permiso de lectura la consulta se prohíbe y no se consulta la base de datos
     */
    @Test
    void getUserById_withoutReadPermission_isForbiddenAndDoesNotQuery() {
        UUID targetId = UUID.randomUUID();

        assertThrows(ForbiddenOperationException.class,
                () -> userService.getUserById(identity(UUID.randomUUID(), UserRole.ESTUDIANTE), targetId));

        verify(userRepository, never()).findById(any());
    }

    /**
     * La respuesta incluye los nombres opcionales solo cuando existen
     */
    @Test
    void getUserById_responseIncludesOptionalNamesOnlyWhenPresent() {
        UUID targetId = UUID.randomUUID();
        UserEntity target = entity(targetId, "carlos.gomez@escuelaing.edu.co");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        UserProfileResponse result = userService.getUserById(identity(UUID.randomUUID(), UserRole.ADMIN),
                targetId);

        assertNull(result.secondName());
        assertNull(result.secondLastName());
        assertFalse(result.roles().isEmpty());
    }
}
