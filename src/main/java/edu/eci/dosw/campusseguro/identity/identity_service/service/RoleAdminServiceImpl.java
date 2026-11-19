package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.AlertRecipientRoleResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RoleDetailResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ConflictException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.RoleNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PermissionRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * Administration of roles and query of the permission catalog (RF-MS01-10), the catalog lives in the database and no
 * operation of this service writes to it (RN-MS01-42)
 *
 * The authorization is decided by the permission that the requester receives in X-User-Permission: ms01:role:read_all
 * to query and ms01:role:manage to create, edit, change permissions and delete
 *
 * Every write operation is audited in MS08 with its result (RNF-MS01-10) and the changes that affect the sessions
 * record the cut in the same transaction (RF-MS01-11): renaming a role cuts the users that have it, changing its
 * permissions cuts the role and deleting it cuts each affected user
 */
@Service
public class RoleAdminServiceImpl implements RoleAdminService {

    private static final Logger log = LoggerFactory.getLogger(RoleAdminServiceImpl.class);

    /** Permissions that every role has and never loses: reading the own profile and logging out */
    private static final List<Permission> MANDATORY_PERMISSIONS = List.of(Permission.MS01_PROFILE_READ,
            Permission.MS01_SESSION_LOGOUT);

    /** Permissions of MS03 that create or edit alerts, and the one that receives them (Role-Permission Matrix) */
    static final String ALERT_MANAGE = "ms03:alert:manage";
    static final String ALERT_MANAGE_DRILL = "ms03:alert:manage_drill";
    static final String ALERT_RECEIVE = "ms03:alert:receive";

    /** Permissions of MS06 that create or edit drills and real events (Role-Permission Matrix) */
    static final String EVENT_DRILL_MANAGE = "ms06:drill:manage";
    static final String EVENT_REAL_MANAGE = "ms06:real_event:manage";

    static final String NAME_EXISTS_MESSAGE = "Ya existe un rol con ese nombre.";
    static final String SYSTEM_RENAME_MESSAGE = "Los roles de sistema no se pueden renombrar.";
    static final String SYSTEM_DELETE_MESSAGE = "Los roles de sistema no se pueden eliminar.";
    static final String MANAGE_EXCLUSIVE_MESSAGE = "El permiso ms01:role:manage es exclusivo del rol ADMIN.";
    static final String ADMIN_MUST_KEEP_MESSAGE = "El rol ADMIN debe conservar el permiso ms01:role:manage.";
    static final String MANDATORY_PERMISSIONS_MESSAGE =
            "El rol %s debe conservar los permisos ms01:profile:read y ms01:session:logout.";
    static final String PROFILE_WRITE_MESSAGE = "El rol %s debe conservar el permiso ms01:profile:write.";

    static final String REASON_NOT_ALLOWED = "SIN_PERMISO";
    static final String REASON_VALIDATION = "VALIDACION";
    static final String REASON_ROLE_NOT_FOUND = "ROL_NO_ENCONTRADO";
    static final String REASON_SYSTEM_ROLE = "ROL_DE_SISTEMA";
    static final String REASON_CONFLICT = "CONFLICTO";
    static final String REASON_BUSINESS_RULE = "REGLA_NEGOCIO";
    static final String REASON_INTERNAL = "ERROR_INTERNO";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;
    private final DatabaseTimeService timeService;

        /**
     * Creates the service with its dependencies
     *
     * @param roleRepository repository to access the roles and their permissions
     * @param permissionRepository repository to access the permission catalog
     * @param userRepository repository to access the users and their roles
     * @param auditRecorder recorder of the audit events of MS08
     * @param timeService single database clock with which the session cuts are set
     */
    public RoleAdminServiceImpl(RoleRepository roleRepository, PermissionRepository permissionRepository,
            UserRepository userRepository, AuditRecorder auditRecorder, DatabaseTimeService timeService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
        this.timeService = timeService;
    }

        /**
     * Lists all the roles with their detail, sorted by name
     *
     * @param requester identity of whoever queries
     * @return the roles with their description, their permissions and the system flag
     * @throws ForbiddenOperationException if the requester does not have the role query permission
     */
    @Override
    @Transactional(readOnly = true)
    public List<RoleDetailResponse> listRoles(RequesterIdentity requester) {
        PermissionChecker.require(requester, Permission.MS01_ROLE_READ_ALL);
        return roleRepository.findAllByOrderByNameAsc().stream()
                .map(RoleAdminServiceImpl::toDetail)
                .toList();
    }

        /**
     * Queries a role by its name
     *
     * @param requester identity of whoever queries
     * @param role name of the role received in the path, it is normalized
     * @return the detail of the role
     * @throws ForbiddenOperationException if the requester does not have the role query permission
     * @throws InvalidRequestException if the name does not have a valid format
     * @throws RoleNotFoundException if the role does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public RoleDetailResponse getRole(RequesterIdentity requester, String role) {
        PermissionChecker.require(requester, Permission.MS01_ROLE_READ_ALL);
        String name = RoleRequestValidator.normalizePathRole(role);
        RoleEntity entity = roleRepository.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException(name));
        return toDetail(entity);
    }

        /**
     * Lists the permission catalog of the database sorted by code
     *
     * @param requester identity of whoever queries
     * @return each permission with its description
     * @throws ForbiddenOperationException if the requester does not have the role query permission
     */
    @Override
    @Transactional(readOnly = true)
    public List<PermissionItemResponse> listPermissions(RequesterIdentity requester) {
        PermissionChecker.require(requester, Permission.MS01_ROLE_READ_ALL);
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(PermissionEntity::getName))
                .map(permission -> new PermissionItemResponse(permission.getName(), permission.getDescription()))
                .toList();
    }

        /**
     * Lists the roles of the database that have ms03:alert:receive sorted by name, with their description and without
     * their permissions
     *
     * @param requester identity of whoever queries
     * @return name and description of each role; empty if no role receives alerts
     * @throws ForbiddenOperationException if the requester has neither ms03:alert:manage nor ms03:alert:manage_drill
     */
    @Override
    @Transactional(readOnly = true)
    public List<AlertRecipientRoleResponse> listAlertRecipientRoles(RequesterIdentity requester) {
        // PermissionChecker only takes the permissions of MS01, the ones of MS03 are checked by their code
        if (!requester.hasPermission(ALERT_MANAGE) && !requester.hasPermission(ALERT_MANAGE_DRILL)) {
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }
        return roleRepository.findWithPermission(ALERT_RECEIVE).stream()
                .map(role -> new AlertRecipientRoleResponse(role.getName(), role.getDescription()))
                .toList();
    }

    /**
     * Lists the roles that can be invited to an event: the ones that receive alerts, because the participants get the
     * alerts that MS06 asks MS03 for. Whoever creates or edits events does not have ms01:role:read_all, so it is
     * authorized with the permissions of MS06 and it does not return the permissions of each role
     *
     * @param requester identity of whoever queries
     * @return name and description of each role, sorted by name; empty if no role receives alerts
     * @throws ForbiddenOperationException if the requester has neither ms06:drill:manage nor ms06:real_event:manage
     */
    @Override
    @Transactional(readOnly = true)
    public List<AlertRecipientRoleResponse> listEventParticipantRoles(RequesterIdentity requester) {
        // PermissionChecker only takes the permissions of MS01, the ones of MS06 are checked by their code
        if (!requester.hasPermission(EVENT_DRILL_MANAGE) && !requester.hasPermission(EVENT_REAL_MANAGE)) {
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }
        return roleRepository.findWithPermission(ALERT_RECEIVE).stream()
                .map(role -> new AlertRecipientRoleResponse(role.getName(), role.getDescription()))
                .toList();
    }

        /**
     * Creates a role that is not a system role and without a session cut (form F-10), silently adds the permissions
     * that every role must have (ms01:profile:read and ms01:session:logout) if the list does not include them, the
     * attempt is audited in MS08
     *
     * @param requester identity of whoever requests
     * @param request name, description and permissions of the role
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the created role
     * @throws ForbiddenOperationException if the requester does not have the role management permission
     * @throws InvalidRequestException if any data breaks form F-10 or any permission is not in the catalog
     * @throws BusinessRuleException if the permissions include ms01:role:manage
     * @throws ConflictException if a role with that name already exists
     */
    @Override
    @Transactional
    public RoleDetailResponse createRole(RequesterIdentity requester, CreateRoleRequest request,
            String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);
        requireManage(AuditAction.CREACION_ROL, requester, corr);

        Map<String, PermissionEntity> catalog = new HashMap<>();
        for (PermissionEntity permission : permissionRepository.findAll()) {
            catalog.put(permission.getName(), permission);
        }

        CreateRoleCommand command;
        try {
            command = RoleRequestValidator.validateCreate(request, catalog.keySet());
        } catch (InvalidRequestException e) {
            reject(AuditAction.CREACION_ROL, requester, RoleNames.normalize(request.getRol()), REASON_VALIDATION,
                    corr);
            throw e;
        }
        if (command.permissions().contains(Permission.MS01_ROLE_MANAGE.getCode())) {
            reject(AuditAction.CREACION_ROL, requester, command.name(), REASON_BUSINESS_RULE, corr);
            throw new BusinessRuleException(MANAGE_EXCLUSIVE_MESSAGE);
        }
        if (roleRepository.existsByName(command.name())) {
            reject(AuditAction.CREACION_ROL, requester, command.name(), REASON_CONFLICT, corr);
            throw new ConflictException(NAME_EXISTS_MESSAGE);
        }

        Set<String> effectivePermissions = new TreeSet<>(command.permissions());
        for (Permission mandatory : MANDATORY_PERMISSIONS) {
            effectivePermissions.add(mandatory.getCode());
        }
        Set<PermissionEntity> selected = new HashSet<>();
        for (String code : effectivePermissions) {
            selected.add(catalog.get(code));
        }

        RoleEntity entity = new RoleEntity();
        entity.setName(command.name());
        entity.setDescription(command.description());
        entity.setPermissions(selected);
        entity.setSystem(false);

        RoleEntity saved;
        try {
            saved = roleRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            reject(AuditAction.CREACION_ROL, requester, command.name(), REASON_CONFLICT, corr);
            log.warn("Role creation rejected by a uniqueness constraint: {}", e.getClass().getSimpleName());
            throw new ConflictException(NAME_EXISTS_MESSAGE);
        } catch (RuntimeException e) {
            reject(AuditAction.CREACION_ROL, requester, command.name(), REASON_INTERNAL, corr);
            log.error("Role creation failed: the role {} could not be saved", command.name(), e);
            throw e;
        }

        String detail = "descripcion=" + command.description() + ";permisos=" + String.join(",", effectivePermissions);
        auditRecorder.recordAs(AuditAction.CREACION_ROL, AuditResult.EXITO, requester,
                AuditRecorder.roleResource(command.name()), detail, corr);
        log.info("Role created by user {}: {}", requester.userId(), command.name());
        return toDetail(saved);
    }

        /**
     * Changes the name and the description of a role (form F-11), if the name changes it records the session cut of the
     * users that have the role, the attempt is audited in MS08
     *
     * @param requester identity of whoever requests
     * @param role current name of the role received in the path, it is normalized
     * @param request new name and description
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the updated role
     * @throws ForbiddenOperationException if the requester does not have the role management permission
     * @throws InvalidRequestException if any data breaks form F-11
     * @throws RoleNotFoundException if the role does not exist
     * @throws ConflictException if it is a system role whose name changes or the new name already exists
     */
    @Override
    @Transactional
    public RoleDetailResponse updateRole(RequesterIdentity requester, String role, UpdateRoleRequest request,
            String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);
        requireManage(AuditAction.EDICION_ROL, requester, corr);
        RoleEntity current = findRole(AuditAction.EDICION_ROL, requester, role, corr);

        UpdateRoleCommand command;
        try {
            command = RoleRequestValidator.validateUpdate(request);
        } catch (InvalidRequestException e) {
            reject(AuditAction.EDICION_ROL, requester, current.getName(), REASON_VALIDATION, corr);
            throw e;
        }

        String previousName = current.getName();
        String previousDescription = current.getDescription();
        boolean renamed = !command.name().equals(previousName);
        if (renamed && current.isSystem()) {
            reject(AuditAction.EDICION_ROL, requester, previousName, REASON_SYSTEM_ROLE, corr);
            throw new ConflictException(SYSTEM_RENAME_MESSAGE);
        }
        if (renamed && roleRepository.existsByName(command.name())) {
            reject(AuditAction.EDICION_ROL, requester, previousName, REASON_CONFLICT, corr);
            throw new ConflictException(NAME_EXISTS_MESSAGE);
        }

        RoleEntity updated;
        try {
            if (renamed) {
                userRepository.markSessionCutByRole(previousName, timeService.now());
                current = roleRepository.findByName(previousName).orElseThrow();
            }
            current.setName(command.name());
            current.setDescription(command.description());
            updated = roleRepository.saveAndFlush(current);
        } catch (DataIntegrityViolationException e) {
            reject(AuditAction.EDICION_ROL, requester, previousName, REASON_CONFLICT, corr);
            log.warn("Role edit rejected by a database constraint: {}",
                    e.getClass().getSimpleName());
            throw new ConflictException(NAME_EXISTS_MESSAGE);
        } catch (RuntimeException e) {
            reject(AuditAction.EDICION_ROL, requester, previousName, REASON_INTERNAL, corr);
            log.error("Role edit failed: the role {} could not be saved", previousName, e);
            throw e;
        }

        String detail = "nombre_anterior=" + previousName + ";descripcion_anterior=" + previousDescription
                + ";nombre_nuevo=" + updated.getName() + ";descripcion_nueva=" + updated.getDescription();
        auditRecorder.recordAs(AuditAction.EDICION_ROL, AuditResult.EXITO, requester,
                AuditRecorder.roleResource(updated.getName()), detail, corr);
        log.info("Role edited by user {}: {}", requester.userId(), updated.getName());
        return toDetail(updated);
    }

        /**
     * Replaces the permissions of a role (form F-12) and records the session cut of the role, the attempt is audited in
     * MS08
     *
     * @param requester identity of whoever requests
     * @param role name of the role received in the path, it is normalized
     * @param request complete list of permissions of the role
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the updated role
     * @throws ForbiddenOperationException if the requester does not have the role management permission
     * @throws InvalidRequestException if any data breaks form F-12 or any permission is not in the catalog
     * @throws RoleNotFoundException if the role does not exist
     * @throws BusinessRuleException if ms01:role:manage stays in a role other than ADMIN or ADMIN loses it, if the role
     *         loses ms01:profile:read or ms01:session:logout, or if ESTUDIANTE or ADMIN lose ms01:profile:write
     */
    @Override
    @Transactional
    public RoleDetailResponse updatePermissions(RequesterIdentity requester, String role,
            UpdateRolePermissionsRequest request, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);
        requireManage(AuditAction.CAMBIO_PERMISOS_ROL, requester, corr);
        RoleEntity entity = findRole(AuditAction.CAMBIO_PERMISOS_ROL, requester, role, corr);

        Map<String, PermissionEntity> catalog = new HashMap<>();
        for (PermissionEntity permission : permissionRepository.findAll()) {
            catalog.put(permission.getName(), permission);
        }

        RolePermissionsCommand command;
        try {
            command = RoleRequestValidator.validatePermissions(request, catalog.keySet());
        } catch (InvalidRequestException e) {
            reject(AuditAction.CAMBIO_PERMISOS_ROL, requester, entity.getName(), REASON_VALIDATION, corr);
            throw e;
        }

        boolean includesManage = command.permissions().contains(Permission.MS01_ROLE_MANAGE.getCode());
        boolean isAdmin = RoleNames.ADMIN.equals(entity.getName());
        if (includesManage && !isAdmin) {
            reject(AuditAction.CAMBIO_PERMISOS_ROL, requester, entity.getName(), REASON_BUSINESS_RULE, corr);
            throw new BusinessRuleException(MANAGE_EXCLUSIVE_MESSAGE);
        }
        if (!includesManage && isAdmin) {
            reject(AuditAction.CAMBIO_PERMISOS_ROL, requester, entity.getName(), REASON_BUSINESS_RULE, corr);
            throw new BusinessRuleException(ADMIN_MUST_KEEP_MESSAGE);
        }
        for (Permission mandatory : MANDATORY_PERMISSIONS) {
            if (!command.permissions().contains(mandatory.getCode())) {
                reject(AuditAction.CAMBIO_PERMISOS_ROL, requester, entity.getName(), REASON_BUSINESS_RULE, corr);
                throw new BusinessRuleException(String.format(MANDATORY_PERMISSIONS_MESSAGE, entity.getName()));
            }
        }
        boolean keepsProfileWrite = command.permissions().contains(Permission.MS01_PROFILE_WRITE.getCode());
        if ((isAdmin || RoleNames.STUDENT.equals(entity.getName())) && !keepsProfileWrite) {
            reject(AuditAction.CAMBIO_PERMISOS_ROL, requester, entity.getName(), REASON_BUSINESS_RULE, corr);
            throw new BusinessRuleException(String.format(PROFILE_WRITE_MESSAGE, entity.getName()));
        }

        Set<String> before = new HashSet<>(PermissionCodes.ofRole(entity));
        Set<String> after = new HashSet<>(command.permissions());
        Set<String> added = new HashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(after);

        RoleEntity saved;
        try {
            Set<PermissionEntity> selected = new HashSet<>();
            for (String code : command.permissions()) {
                selected.add(catalog.get(code));
            }
            entity.setPermissions(selected);
            entity.setValidFrom(timeService.now());
            saved = roleRepository.saveAndFlush(entity);
        } catch (RuntimeException e) {
            reject(AuditAction.CAMBIO_PERMISOS_ROL, requester, entity.getName(), REASON_INTERNAL, corr);
            log.error("Permission change failed: the role {} could not be saved", entity.getName(), e);
            throw e;
        }

        String detail = "agregados=" + String.join(",", RoleNames.sorted(added)) + ";retirados="
                + String.join(",", RoleNames.sorted(removed));
        auditRecorder.recordAs(AuditAction.CAMBIO_PERMISOS_ROL, AuditResult.EXITO, requester,
                AuditRecorder.roleResource(saved.getName()), detail, corr);
        log.info("Permissions of role {} changed by user {}", saved.getName(), requester.userId());
        return toDetail(saved);
    }

        /**
     * Deletes a role (form F-13): those who have only that role move to ESTUDIANTE, the rest have the role removed and
     * the session cut of each one is recorded, all in a single transaction, the attempt is audited in MS08
     *
     * @param requester identity of whoever requests
     * @param role name of the role received in the path, it is normalized
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @throws ForbiddenOperationException if the requester does not have the role management permission
     * @throws InvalidRequestException if the name does not have a valid format
     * @throws RoleNotFoundException if the role does not exist
     * @throws ConflictException if the role is a system role
     */
    @Override
    @Transactional
    public void deleteRole(RequesterIdentity requester, String role, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);
        requireManage(AuditAction.ELIMINACION_ROL, requester, corr);
        RoleEntity entity = findRole(AuditAction.ELIMINACION_ROL, requester, role, corr);
        String name = entity.getName();

        if (entity.isSystem()) {
            reject(AuditAction.ELIMINACION_ROL, requester, name, REASON_SYSTEM_ROLE, corr);
            throw new ConflictException(SYSTEM_DELETE_MESSAGE);
        }

        long movedToStudent;
        long onlyLostRole;
        try {
            movedToStudent = userRepository.countWithOnlyRole(name);
            onlyLostRole = userRepository.countWithRole(name) - movedToStudent;
            userRepository.markSessionCutByRole(name, timeService.now());
            userRepository.replaceOnlyRole(name, RoleNames.STUDENT);
            userRepository.removeRoleFromAll(name);
            roleRepository.delete(entity);
            roleRepository.flush();
        } catch (RuntimeException e) {
            reject(AuditAction.ELIMINACION_ROL, requester, name, REASON_INTERNAL, corr);
            log.error("Role deletion failed: the role {} could not be deleted", name, e);
            throw e;
        }

        String detail = "usuarios_a_estudiante=" + movedToStudent + ";usuarios_que_solo_perdieron_el_rol="
                + onlyLostRole;
        auditRecorder.recordAs(AuditAction.ELIMINACION_ROL, AuditResult.EXITO, requester,
                AuditRecorder.roleResource(name), detail, corr);
        log.info("Role {} deleted by user {}: {} user(s) moved to {} and {} lost the role", name,
                requester.userId(), movedToStudent, RoleNames.STUDENT, onlyLostRole);
    }

        /**
     * Requires the role management permission, if it is missing it audits the rejection and answers 403
     *
     * @param action audit action of the operation
     * @param requester identity of whoever requests
     * @param corr correlation id of the request
     * @throws ForbiddenOperationException if the requester does not have the role management permission
     */
    private void requireManage(AuditAction action, RequesterIdentity requester, String corr) {
        if (!requester.hasPermission(Permission.MS01_ROLE_MANAGE)) {
            reject(action, requester, null, REASON_NOT_ALLOWED, corr);
            log.warn("Role operation rejected: the requester {} does not have the role management permission",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }
    }

        /**
     * Normalizes the name of the path and looks up the role, if the format is not valid or the role does not exist it
     * audits the rejection and throws the error
     *
     * @param action audit action of the operation
     * @param requester identity of whoever requests
     * @param rawRole name of the role received in the path
     * @param corr correlation id of the request
     * @return the role found
     * @throws InvalidRequestException if the name does not have a valid format
     * @throws RoleNotFoundException if the role does not exist
     */
    private RoleEntity findRole(AuditAction action, RequesterIdentity requester, String rawRole, String corr) {
        String name;
        try {
            name = RoleRequestValidator.normalizePathRole(rawRole);
        } catch (InvalidRequestException e) {
            reject(action, requester, null, REASON_VALIDATION, corr);
            throw e;
        }
        return roleRepository.findByName(name).orElseThrow(() -> {
            reject(action, requester, name, REASON_ROLE_NOT_FOUND, corr);
            return new RoleNotFoundException(name);
        });
    }

        /**
     * Audits in MS08 the rejection of an operation on roles
     *
     * @param action audit action of the operation
     * @param requester identity of whoever made the request
     * @param roleName affected role, null if the rejection does not correspond to a known role
     * @param reason reason for the rejection
     * @param corr correlation id of the request
     */
    private void reject(AuditAction action, RequesterIdentity requester, String roleName, String reason,
            String corr) {
        auditRecorder.recordAs(action, AuditResult.FALLO, requester, AuditRecorder.roleResource(roleName), reason,
                corr);
    }

        /**
     * Converts a stored role into its detail for the API, with the current permissions of the catalog
     *
     * @param role stored role
     * @return the detail of the role
     */
    private static RoleDetailResponse toDetail(RoleEntity role) {
        return new RoleDetailResponse(role.getName(), role.getDescription(), PermissionCodes.ofRole(role),
                role.isSystem());
    }
}
