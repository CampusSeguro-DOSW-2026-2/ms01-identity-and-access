package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.ArrayList;
import java.util.EnumSet;
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
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.FieldRules;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * Registro de usuarios (RF-MS01-01), perfil propio (RF-MS01-06) y administración de usuarios
 * (RF-MS01-07)
 *
 * La autorización de cada operación la decide el permiso que concede la matriz Rol-Permiso y nunca el
 * nombre de un rol, el registro y el cambio de estado se auditan en MS08 (RNF-MS01-10)
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
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;
    private final RequestValidator requestValidator;
    private final Set<String> institutionalDomains;

    /**
     * Crea el servicio con sus dependencias
     *
     * @param userRepository repositorio de acceso a los usuarios y sus roles
     * @param passwordEncoder codificador con el que se guardan las contraseñas
     * @param auditRecorder registrador de los eventos de auditoría de MS08
     * @param requestValidator validador de los cuerpos de las peticiones
     * @param institutionalDomains dominios de correo institucional configurados, sin la arroba, se ignoran
     *        los espacios, las mayúsculas y los valores vacíos
     */
    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuditRecorder auditRecorder, RequestValidator requestValidator,
            @Value("${identity.institutional-domains:escuelaing.edu.co,mail.escuelaing.edu.co}")
            List<String> institutionalDomains) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
        this.requestValidator = requestValidator;
        this.institutionalDomains = institutionalDomains.stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Registra un usuario institucional (RF-MS01-01), cada intento, exitoso o fallido, se audita en MS08
     *
     * @param request datos del formulario de registro
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return el usuario creado con su id, correo, roles y estado
     * @throws InvalidRequestException si los datos incumplen el formulario F-01
     * @throws EmailAlreadyExistsException si el correo ya está registrado
     */
    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        List<ErrorResponse.ErrorDetail> errors = collectRegistrationErrors(request);
        if (!errors.isEmpty()) {
            auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.FALLO, null, null, REASON_VALIDATION,
                    corr);
            log.warn("Registro rechazado por datos inválidos");
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
        entity.setRoles(EnumSet.of(UserRole.ESTUDIANTE));
        entity.setState(UserState.ACTIVO);

        UserEntity saved;
        try {
            saved = userRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            log.warn("Registro rechazado por restricción de unicidad: {}", e.getClass().getSimpleName());
            throw emailConflict(corr);
        }

        auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.EXITO, null,
                AuditRecorder.userResource(saved.getId()), null, corr);
        log.info("Usuario registrado. usuario={}", saved.getId());
        return UserResponse.fromEntity(saved);
    }

    /**
     * Consulta el perfil del propio solicitante (RF-MS01-03, RF-MS01-06)
     *
     * @param requester identidad de quien consulta
     * @return los datos del usuario identificado por la cabecera de identidad
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws UserNotFoundException si el usuario ya no existe
     */
    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(RequesterIdentity requester) {
        PermissionChecker.require(requester, Permission.MS01_PROFILE_READ);
        UserEntity entity = findUser(requester.userId());
        return UserProfileResponse.fromEntity(entity);
    }

    /**
     * Actualiza el perfil propio (RF-MS01-06), la actualización reemplaza el perfil completo
     *
     * @param requester identidad de quien actualiza
     * @param request nombres y apellidos nuevos
     * @return el perfil actualizado
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws InvalidRequestException si los datos incumplen el formulario F-03
     * @throws UserNotFoundException si el usuario ya no existe
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
     * Consulta un usuario por su id (RF-MS01-05, RF-MS01-07)
     *
     * @param requester identidad de quien consulta
     * @param id id del usuario consultado
     * @return los datos del usuario
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws UserNotFoundException si el usuario no existe
     */
    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(RequesterIdentity requester, UUID id) {
        PermissionChecker.require(requester, Permission.MS01_USER_READ);
        UserEntity entity = findUser(id);
        return UserProfileResponse.fromEntity(entity);
    }

    /**
     * Lista y busca usuarios con paginación y filtros opcionales (RF-MS01-07, formulario F-07), el
     * orden es por fecha de registro descendente y luego por id
     *
     * @param requester identidad de quien consulta
     * @param correo búsqueda parcial por correo, puede ser null
     * @param estado filtro de estado, puede ser null
     * @param rol filtro de rol, puede ser null
     * @param page número de página recibido como texto, null equivale a 0
     * @param size tamaño de página recibido como texto, null equivale a 20
     * @return la página de usuarios con sus roles completos
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     * @throws InvalidRequestException si algún parámetro incumple el formulario F-07
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> search(RequesterIdentity requester, String correo, String estado,
            String rol, String page, String size) {
        PermissionChecker.require(requester, Permission.MS01_USER_READ);
        UserSearchCriteria criteria = UserSearchValidator.validate(correo, estado, rol, page, size);

        Sort order = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), order);
        Page<UserEntity> result = userRepository.searchUsers(criteria.email(), criteria.state(), criteria.role(),
                pageable);

        return PageResponse.from(result.map(UserProfileResponse::fromEntity));
    }

    /**
     * Activa o inactiva a uno o varios usuarios en una sola operación atómica (RF-MS01-07, formulario
     * F-08), cada usuario procesado se audita en MS08
     *
     * @param requester identidad de quien solicita
     * @param request usuarios objetivo y nuevo estado
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return el estado anterior y el nuevo de cada usuario
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido o se incluye a sí
     *         mismo
     * @throws InvalidRequestException si el cuerpo incumple el formulario F-08
     * @throws UsersNotFoundException si algún usuario objetivo no existe
     */
    @Override
    @Transactional
    public ChangeStatusResponse changeUserStatus(RequesterIdentity requester, ChangeStatusRequest request,
            String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        if (!requester.hasPermission(Permission.MS01_USER_STATUS)) {
            auditRecorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.FALLO, requester, null,
                    REASON_NOT_ALLOWED, corr);
            log.warn("Cambio de estado rechazado: el solicitante {} no tiene el permiso requerido",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }

        requestValidator.validate(request);
        Set<UUID> targetIds = request.toUserIds();
        UserState newState = request.toState();

        if (targetIds.contains(requester.userId())) {
            auditRecorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.FALLO, requester,
                    AuditRecorder.userResource(requester.userId()), REASON_SELF_CHANGE, corr);
            log.warn("Cambio de estado rechazado: el administrador {} intenta cambiar su propio estado",
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

        applyState(requester, targetIds, locked, newState, corr);

        List<IndividualChangeStateResponse> results = new ArrayList<>();
        for (UUID id : targetIds) {
            UserEntity user = usersById.get(id);
            UserState previous = previousStates.get(id);
            results.add(IndividualChangeStateResponse.fromEntity(user, previous));
            auditRecorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.EXITO, requester,
                    AuditRecorder.userResource(id), stateChangeDetail(previous, user.getState()), corr);
        }
        log.info("Estado actualizado por el administrador {}: {} usuario(s) procesado(s)", requester.userId(),
                results.size());
        return ChangeStatusResponse.fromEntity(results);
    }

    /**
     * Comprueba que todos los ids pedidos correspondan a un usuario existente, si falta alguno audita un
     * FALLO por cada id inexistente y lanza 404 sin haber cambiado nada
     *
     * @param requester identidad de quien solicita
     * @param targetIds ids pedidos
     * @param usersById usuarios encontrados en la base de datos indexados por id
     * @param corr correlation id de la petición
     * @throws UsersNotFoundException si algún id pedido no corresponde a un usuario
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
        log.warn("Cambio de estado rechazado: {} usuario(s) inexistente(s)", missing.size());
        throw new UsersNotFoundException(missing);
    }

    /**
     * Asigna el nuevo estado a los usuarios y lo confirma en la base de datos, si la escritura falla audita
     * el fallo de cada usuario, no se aplica ningún cambio y se propaga el error
     *
     * @param requester identidad de quien solicita
     * @param targetIds ids de los usuarios a modificar
     * @param users usuarios cargados con bloqueo de escritura
     * @param newState estado que se asigna a todos
     * @param corr correlation id de la petición
     */
    private void applyState(RequesterIdentity requester, Set<UUID> targetIds, List<UserEntity> users,
            UserState newState, String corr) {
        for (UserEntity user : users) {
            user.setState(newState);
        }
        try {
            userRepository.saveAllAndFlush(users);
        } catch (RuntimeException e) {
            for (UUID id : targetIds) {
                auditRecorder.recordAs(AuditAction.CAMBIO_ESTADO, AuditResult.FALLO, requester,
                        AuditRecorder.userResource(id), REASON_INTERNAL, corr);
            }
            log.error("Cambio de estado fallido: no fue posible guardar los cambios", e);
            throw e;
        }
    }

    /**
     * Indexa una lista de usuarios por su id
     *
     * @param users usuarios cargados
     * @return mapa de id a usuario
     */
    private static Map<UUID, UserEntity> indexById(List<UserEntity> users) {
        Map<UUID, UserEntity> index = new HashMap<>();
        for (UserEntity user : users) {
            index.put(user.getId(), user);
        }
        return index;
    }

    /**
     * Construye el detalle de auditoría de un cambio de estado
     *
     * @param previous estado que tenía el usuario
     * @param current estado que tiene el usuario después del cambio
     * @return texto con el estado anterior y el nuevo
     */
    private static String stateChangeDetail(UserState previous, UserState current) {
        return "estado_anterior=" + previous + ";estado_nuevo=" + current;
    }

    /**
     * Reúne los errores del formulario de registro: los de formato y reglas de cada campo, y que el correo
     * pertenezca al dominio institucional, sin indicar si el correo falló por dominio o por formato
     *
     * @param request datos del formulario de registro
     * @return errores por campo, vacío si el formulario es válido
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
     * Indica si el correo pertenece a alguno de los dominios institucionales configurados
     *
     * @param email correo ya normalizado a minúsculas
     * @return true si la parte posterior a la arroba es igual a alguno de los dominios institucionales
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
     * Audita el rechazo por correo duplicado y crea la excepción que se lanza al cliente
     *
     * @param corr correlation id de la petición
     * @return la excepción de correo ya registrado
     */
    private EmailAlreadyExistsException emailConflict(String corr) {
        auditRecorder.recordAnonymous(AuditAction.REGISTRO, AuditResult.FALLO, null, null, REASON_CONFLICT, corr);
        log.warn("Registro rechazado: correo ya registrado");
        return new EmailAlreadyExistsException();
    }

    /**
     * Busca un usuario por id
     *
     * @param id id del usuario
     * @return el usuario encontrado
     * @throws UserNotFoundException si no existe
     */
    private UserEntity findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
