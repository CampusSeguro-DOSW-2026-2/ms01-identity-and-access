package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserServiceImpl;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;

/**
 * Tests of the own profile (RF-MS01-06) and the query by id (RF-MS01-07), the registration is tested in
 * RegistrationServiceTest
 */
class UserServiceTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private AuditEventPublisher auditPublisher;
    private UserServiceImpl userService;

        /**
     * Prepares the service with its simulated dependencies
     */
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        auditPublisher = mock(AuditEventPublisher.class);
        userService = service();
    }

        /**
     * Builds the service with the simulated dependencies
     *
     * @return the configured service
     */
    private UserServiceImpl service() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        AuditRecorder auditRecorder = new AuditRecorder(auditPublisher, clock);
        RequestValidator requestValidator = new RequestValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
        return new UserServiceImpl(userRepository, roleRepository, auditRecorder, requestValidator,
                FixedDatabaseTime.at(Instant.parse("2026-03-01T10:00:00Z")));
    }

        /**
     * Builds a sample stored user
     *
     * @param id identifier of the user
     * @param email email of the user
     * @return the sample user
     */
    private UserEntity entity(UUID id, String email) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setFirstName("Carlos");
        entity.setFirstLastName("Gomez");
        entity.setEmail(email);
        entity.setPassword("$2a$10$hash");
        entity.setRoles(TestEntities.roles("ESTUDIANTE"));
        entity.setState(UserState.ACTIVO);
        return entity;
    }

        /**
     * Builds the identity of a requester
     *
     * @param id identifier of the requester
     * @param roles roles of the requester
     * @return the identity of the requester
     */
    private RequesterIdentity identity(UUID id, String... roles) {
        return TestIdentities.of(id, roles);
    }

        /**
     * The query of the own profile returns the profile of the user
     */
    @Test
    void getMyProfile_existingUser_returnsProfile() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(entity(id, "carlos.gomez@escuelaing.edu.co")));

        UserProfileResponse result = userService.getMyProfile(identity(id, "ESTUDIANTE"));

        assertEquals("carlos.gomez@escuelaing.edu.co", result.email());
        assertEquals("Carlos", result.firstName());
    }

        /**
     * The query of the own profile of a non-existent user throws not found
     */
    @Test
    void getMyProfile_unknownUser_throwsUserNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.getMyProfile(identity(id, "ESTUDIANTE")));
    }

        /**
     * A valid update modifies and returns the profile
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

        UserProfileResponse result = userService.updateProfile(identity(id, "ESTUDIANTE"), request);

        assertEquals("Sebastian", result.firstName());
        assertEquals("carlos.gomez@escuelaing.edu.co", result.email());
        verify(userRepository).saveAndFlush(any(UserEntity.class));
    }

        /**
     * Invalid names throw invalid request and nothing is saved
     */
    @Test
    void updateProfile_invalidNames_throwsInvalidRequestAndDoesNotSave() {
        UUID id = UUID.randomUUID();
        UserUpdateProfileRequest request = new UserUpdateProfileRequest();
        request.setFirstName("1");
        request.setFirstLastName("Gomez");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> userService.updateProfile(identity(id, "ESTUDIANTE"), request));

        assertEquals("primer_nombre", error.getErrors().get(0).campo());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
    }

        /**
     * The rejected fields report the messages of the form
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
                () -> userService.updateProfile(identity(id, "ESTUDIANTE"), request));

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
     * The query of a user by id with permission returns their profile
     */
    @Test
    void getUserById_withReadPermission_returnsProfile() {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(entity(targetId, "carlos.gomez@escuelaing.edu.co")));

        UserProfileResponse result = userService.getUserById(identity(requesterId, "AUDITOR"), targetId);

        assertEquals(targetId, result.id());
        assertEquals("carlos.gomez@escuelaing.edu.co", result.email());
    }

        /**
     * The query of a non-existent user throws not found
     */
    @Test
    void getUserById_unknownUser_throwsUserNotFoundException() {
        UUID targetId = UUID.randomUUID();
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.getUserById(identity(UUID.randomUUID(), "ADMIN"), targetId));
    }

        /**
     * Without read permission the query is forbidden and the database is not queried
     */
    @Test
    void getUserById_withoutReadPermission_isForbiddenAndDoesNotQuery() {
        UUID targetId = UUID.randomUUID();

        assertThrows(ForbiddenOperationException.class,
                () -> userService.getUserById(identity(UUID.randomUUID(), "ESTUDIANTE"), targetId));

        verify(userRepository, never()).findById(any());
    }

        /**
     * The response includes the optional names only when they exist
     */
    @Test
    void getUserById_responseIncludesOptionalNamesOnlyWhenPresent() {
        UUID targetId = UUID.randomUUID();
        UserEntity target = entity(targetId, "carlos.gomez@escuelaing.edu.co");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        UserProfileResponse result = userService.getUserById(identity(UUID.randomUUID(), "ADMIN"),
                targetId);

        assertNull(result.secondName());
        assertNull(result.secondLastName());
        assertFalse(result.roles().isEmpty());
    }
}
