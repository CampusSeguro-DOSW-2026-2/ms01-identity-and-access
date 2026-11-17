package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.IndividualChangeStateResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * User registration (RF-MS01-01), own profile (RF-MS01-06) and user administration (RF-MS01-07)
 *
 * The authorization of each operation is decided by the permission that the requester receives in X-User-Permission and
 * never by the name of a role, the registration and the status change are audited in MS08 (RNF-MS01-10), the status
 * change also records the session cut of each user (RF-MS01-11)
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    static final String FIELD_EMAIL = "correo";

    static final String SELF_STATUS_CHANGE_MESSAGE = "No puede cambiar su propio estado.";

    static final String REASON_VALIDATION = "VALIDACION";
    static final String REASON_CONFLICT = "CONFLICTO";
    static final String REASON_NOT_ALLOWED = "SIN_PERMISO";
    static final String REASON_SELF_CHANGE = "AUTOMODIFICACION_DE_ESTADO";
    static final String REASON_USER_NOT_FOUND = "USUARIO_NO_ENCONTRADO";
    static final String REASON_INTERNAL = "ERROR_INTERNO";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;
    private final RequestValidator requestValidator;
    private final DatabaseTimeService timeService;
    private final Set<String> institutionalDomains;

        /**
     * Creates the service with its dependencies
     *
     * @param userRepository repository to access the users and their roles
     * @param roleRepository repository to access the roles
     * @param passwordEncoder encoder with which the passwords are stored
     * @param auditRecorder recorder of the audit events of MS08
     * @param requestValidator validator of the request bodies
     * @param timeService single database clock with which the session cuts are set
     * @param institutionalDomains configured institutional email domains, without the at sign, spaces, uppercase and
     *        empty values are ignored
     */
    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder, AuditRecorder auditRecorder, RequestValidator requestValidator,
            DatabaseTimeService timeService,
            @Value("${identity.institutional-domains:escuelaing.edu.co,mail.escuelaing.edu.co}")
            List<String> institutionalDomains) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
        this.requestValidator = requestValidator;
        this.timeService = timeService;
        this.institutionalDomains = institutionalDomains.stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

        /**
     * Registers an institutional user (RF-MS01-01), every attempt, successful or failed, is audited in MS08
     *
     * @param request data of the registration form
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the created user with its id, email, roles and status
     * @throws InvalidRequestException if the data breaks form F-01
     * @throws EmailAlreadyExistsException if the email is already registered
     */
    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        List<ErrorResponse.ErrorDetail> errors = collectRegistrationErrors(request);
        if (!errors.isEmpty()) {
            auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.FALLO, null, null, REASON_VALIDATION,
                    corr);
            log.warn("Registration rejected because of invalid data");
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, errors);
        }

        String email = request.getEmail();
        if (userRepository.existsByEmail(email)) {
            throw emailConflict(corr);
        }

        UserEntity entity = new UserEntity();
        entity.setFirstName(request.getFirstName());
        entity.setSecondName(request.getSecondName());
        entity.setFirstLastName(request.getFirstLastName());
        entity.setSecondLastName(request.getSecondLastName());
        entity.setEmail(email);
        entity.setPassword(passwordEncoder.encode(request.getPassword()));
        RoleEntity studentRole = roleRepository.findByName(RoleNames.STUDENT).orElseThrow(
                () -> new IllegalStateException("The system role " + RoleNames.STUDENT + " does not exist"));
        entity.setRoles(Set.of(studentRole));
        entity.setState(UserState.ACTIVO);

        UserEntity saved;
        try {
            saved = userRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            log.warn("Registration rejected by a uniqueness constraint: {}", e.getClass().getSimpleName());
            throw emailConflict(corr);
        }

        auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.EXITO, null,
                AuditRecorder.userResource(saved.getId()), null, corr);
        log.info("Usuario registrado. usuario={}", saved.getId());
        return UserResponse.fromEntity(saved);
    }

        /**
     * Queries the profile of the requester themselves (RF-MS01-03, RF-MS01-06)
     *
     * @param requester identity of whoever queries
     * @return the data of the user identified by the identity header
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws UserNotFoundException if the user no longer exists
     */
    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(RequesterIdentity requester) {
        PermissionChecker.require(requester, Permission.MS01_PROFILE_READ);
        UserEntity entity = findUser(requester.userId());
        return UserProfileResponse.fromEntity(entity);
    }

        /**
     * Updates the own profile (RF-MS01-06), the update replaces the complete profile
     *
     * @param requester identity of whoever updates
     * @param request new names and lastnames
     * @return the updated profile
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws InvalidRequestException if the data breaks form F-03
     * @throws UserNotFoundException if the user no longer exists
     */
    @Override
    @Transactional
    public UserProfileResponse updateProfile(RequesterIdentity requester, UserUpdateProfileRequest request) {
        PermissionChecker.require(requester, Permission.MS01_PROFILE_WRITE);
        requestValidator.validate(request);

        UserEntity entity = findUser(requester.userId());
        entity.setFirstName(request.getFirstName());
        entity.setSecondName(request.getSecondName());
        entity.setFirstLastName(request.getFirstLastName());
        entity.setSecondLastName(request.getSecondLastName());

        UserEntity updated = userRepository.saveAndFlush(entity);
        return UserProfileResponse.fromEntity(updated);
    }

        /**
     * Queries a user by their id (RF-MS01-05, RF-MS01-07)
     *
     * @param requester identity of whoever queries
     * @param id id of the queried user
     * @return the data of the user
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws UserNotFoundException if the user does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(RequesterIdentity requester, UUID id) {
        PermissionChecker.require(requester, Permission.MS01_USER_READ);
        UserEntity entity = findUser(id);
        return UserProfileResponse.fromEntity(entity);
    }

        /**
     * Lists and searches users with pagination and optional filters (RF-MS01-07, form F-07), the order is by
     * registration date descending and then by id
     *
     * @param requester identity of whoever queries
     * @param correo partial search by email, may be null
     * @param estado status filter, may be null
     * @param rol role filter, may be null
     * @param page page number received as text, null is equivalent to 0
     * @param size page size received as text, null is equivalent to 20
     * @return the page of users with their complete roles
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws InvalidRequestException if any parameter breaks form F-07 or the role does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> search(RequesterIdentity requester, String correo, String estado,
            String rol, String page, String size) {
        PermissionChecker.require(requester, Permission.MS01_USER_READ);
        UserSearchCriteria criteria = UserSearchValidator.validate(correo, estado, rol, page, size);
        if (criteria.role() != null && !roleRepository.existsByName(criteria.role())) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, List.of(
                    new ErrorResponse.ErrorDetail(UserSearchValidator.FIELD_ROLE,
                            UserSearchValidator.INVALID_ROLE_MESSAGE)));
        }

        Sort order = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), order);
        Page<UserEntity> result = userRepository.searchUsers(criteria.email(), criteria.state(), criteria.role(),
                pageable);

        return PageResponse.from(result.map(UserProfileResponse::fromEntity));
    }

        /**
     * Activates or deactivates one or several users in a single atomic operation (RF-MS01-07, form F-08), every
     * processed user is audited in MS08
     *
     * @param requester identity of whoever requests
     * @param request target users and new status
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the previous and the new status of each user
     * @throws ForbiddenOperationException if the requester does not have the required permission or includes themselves
     * @throws InvalidRequestException if the body breaks form F-08
     * @throws UsersNotFoundException if any target user does not exist
     */
    @Override
    @Transactional
    public ChangeStatusResponse changeUserStatus(RequesterIdentity requester, ChangeStatusRequest request,
            String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        if (!requester.hasPermission(Permission.MS01_USER_STATUS)) {
            auditRecorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.FALLO, requester, null,
                    REASON_NOT_ALLOWED, corr);
            log.warn("Status change rejected: the requester {} does not have the required permission",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }

        requestValidator.validate(request);
        Set<UUID> targetIds = request.toUserIds();
        UserState newState = request.toState();

        if (targetIds.contains(requester.userId())) {
            auditRecorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.FALLO, requester,
                    AuditRecorder.userResource(requester.userId()), REASON_SELF_CHANGE, corr);
            log.warn("Status change rejected: the administrator {} tries to change their own status",
                    requester.userId());
            throw new ForbiddenOperationException(SELF_STATUS_CHANGE_MESSAGE);
        }

        List<UserEntity> locked = userRepository.findAllByIdInOrderById(targetIds);
        Map<UUID, UserEntity> usersById = indexById(locked);
        verifyAllUsersExist(requester, targetIds, usersById, corr);

        Map<UUID, UserState> previousStates = new LinkedHashMap<>();
        for (UUID id : targetIds) {
            previousStates.put(id, usersById.get(id).getState());
        }

        Instant cut = timeService.now();
        applyState(requester, targetIds, locked, newState, cut, corr);

        List<IndividualChangeStateResponse> results = new ArrayList<>();
        for (UUID id : targetIds) {
            UserEntity user = usersById.get(id);
            UserState previous = previousStates.get(id);
            results.add(IndividualChangeStateResponse.fromEntity(user, previous));
            auditRecorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.EXITO, requester,
                    AuditRecorder.userResource(id), stateChangeDetail(previous, user.getState()), corr);
        }
        log.info("Status updated by administrator {}: {} user(s) processed", requester.userId(),
                results.size());
        return ChangeStatusResponse.fromEntity(results);
    }

        /**
     * Internal query of several users by id (RF-MS01-09), it is authorized by the internal security chain through the
     * calling service and is not audited
     *
     * @param request ids of the users, from 1 to 100
     * @return the users found in the requested order, without repeated ones, and the ids that do not exist
     * @throws InvalidRequestException if the list is missing, has more than 100 ids or any id is not a UUID
     */
    @Override
    @Transactional(readOnly = true)
    public BatchUsersResponse findUsersByIds(BatchUsersRequest request) {
        requestValidator.validate(request);
        Set<UUID> ids = request.toUserIds();

        Map<UUID, UserEntity> byId = new HashMap<>();
        for (UserEntity user : userRepository.findAllById(ids)) {
            byId.put(user.getId(), user);
        }
        List<UserProfileResponse> found = new ArrayList<>();
        List<UUID> missing = new ArrayList<>();
        for (UUID id : ids) {
            UserEntity user = byId.get(id);
            if (user == null) {
                missing.add(id);
            } else {
                found.add(UserProfileResponse.fromEntity(user));
            }
        }
        return new BatchUsersResponse(found, missing);
    }

        /**
     * Internal query of the users that have a role (RF-MS01-09), sorted by id, it is authorized by the internal
     * security chain through the calling service and is not audited
     *
     * @param rol required role
     * @param creadoAntes cut-off instant in ISO 8601, may be null
     * @param page page number received as text, null is equivalent to 0
     * @param size page size received as text, null is equivalent to 20
     * @return the page of users with that role, active and inactive
     * @throws InvalidRequestException if any parameter is not valid or the role does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> searchByRole(String rol, String creadoAntes, String page,
            String size) {
        RoleSearchCriteria criteria = UserSearchValidator.validateByRole(rol, creadoAntes, page, size);
        if (!roleRepository.existsByName(criteria.role())) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, List.of(
                    new ErrorResponse.ErrorDetail(UserSearchValidator.FIELD_ROLE,
                            UserSearchValidator.INVALID_ROLE_MESSAGE)));
        }

        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Order.asc("id")));
        Page<UserEntity> result = criteria.createdBefore() == null
                ? userRepository.findByRole(criteria.role(), pageable)
                : userRepository.findByRoleCreatedUpTo(criteria.role(), criteria.createdBefore(), pageable);

        return PageResponse.from(result.map(UserProfileResponse::fromEntity));
    }

        /**
     * Checks that all the requested ids correspond to an existing user, if any is missing it audits a FALLO for each
     * non-existent id and throws 404 without having changed anything
     *
     * @param requester identity of whoever requests
     * @param targetIds requested ids
     * @param usersById users found in the database indexed by id
     * @param corr correlation id of the request
     * @throws UsersNotFoundException if any requested id does not correspond to a user
     */
    private void verifyAllUsersExist(RequesterIdentity requester, Set<UUID> targetIds,
            Map<UUID, UserEntity> usersById, String corr) {
        List<UUID> missing = new ArrayList<>();
        for (UUID id : targetIds) {
            if (!usersById.containsKey(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        for (UUID id : missing) {
            auditRecorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.FALLO, requester,
                    AuditRecorder.userResource(id), REASON_USER_NOT_FOUND, corr);
        }
        log.warn("Status change rejected: {} non-existent user(s)", missing.size());
        throw new UsersNotFoundException(missing);
    }

        /**
     * Assigns the new status to the users and commits it to the database, if the write fails it audits the failure of
     * each user, no change is applied and the error is propagated
     *
     * @param requester identity of whoever requests
     * @param targetIds ids of the users to modify
     * @param users users loaded with a write lock
     * @param newState status that is assigned to all
     * @param cut instant of the session cut of each user, truncated to seconds
     * @param corr correlation id of the request
     */
    private void applyState(RequesterIdentity requester, Set<UUID> targetIds, List<UserEntity> users,
            UserState newState, Instant cut, String corr) {
        for (UserEntity user : users) {
            user.setState(newState);
            user.setValidFrom(cut);
        }
        try {
            userRepository.saveAllAndFlush(users);
        } catch (RuntimeException e) {
            for (UUID id : targetIds) {
                auditRecorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.FALLO, requester,
                        AuditRecorder.userResource(id), REASON_INTERNAL, corr);
            }
            log.error("Status change failed: the changes could not be saved", e);
            throw e;
        }
    }

        /**
     * Indexes a list of users by their id
     *
     * @param users loaded users
     * @return map from id to user
     */
    private static Map<UUID, UserEntity> indexById(List<UserEntity> users) {
        Map<UUID, UserEntity> index = new HashMap<>();
        for (UserEntity user : users) {
            index.put(user.getId(), user);
        }
        return index;
    }

        /**
     * Builds the audit detail of a status change
     *
     * @param previous status that the user had
     * @param current status that the user has after the change
     * @return text with the previous and the new status
     */
    private static String stateChangeDetail(UserState previous, UserState current) {
        return "estado_anterior=" + previous + ";estado_nuevo=" + current;
    }

        /**
     * Gathers the errors of the registration form: those of format and rules of each field, and that the email belongs
     * to the institutional domain, without indicating whether the email failed because of the domain or the format
     *
     * @param request data of the registration form
     * @return errors by field, empty if the form is valid
     */
    private List<ErrorResponse.ErrorDetail> collectRegistrationErrors(UserCreateRequest request) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>(requestValidator.collectErrors(request));
        boolean emailAlreadyInvalid = false;
        for (ErrorResponse.ErrorDetail error : errors) {
            if (FIELD_EMAIL.equals(error.campo())) {
                emailAlreadyInvalid = true;
            }
        }
        if (!emailAlreadyInvalid && !belongsToInstitutionalDomain(request.getEmail())) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_EMAIL, FieldRules.EMAIL_REGISTER_MESSAGE));
        }
        return ValidationErrors.merge(errors);
    }

        /**
     * Tells whether the email belongs to any of the configured institutional domains
     *
     * @param email email already normalized to lowercase
     * @return true if the part after the at sign is equal to any of the institutional domains
     */
    private boolean belongsToInstitutionalDomain(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return false;
        }
        return institutionalDomains.contains(email.substring(at + 1));
    }

        /**
     * Audits the rejection for a duplicated email and creates the exception that is thrown to the client
     *
     * @param corr correlation id of the request
     * @return the exception of an already registered email
     */
    private EmailAlreadyExistsException emailConflict(String corr) {
        auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.FALLO, null, null, REASON_CONFLICT, corr);
        log.warn("Registration rejected: email already registered");
        return new EmailAlreadyExistsException();
    }

        /**
     * Looks up a user by id
     *
     * @param id id of the user
     * @return the user found
     * @throws UserNotFoundException if it does not exist
     */
    private UserEntity findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
