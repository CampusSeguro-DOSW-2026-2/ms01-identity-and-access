package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.util.GlobalValues;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * Management of roles and permissions (RF-MS01-05)
 *
 * The query of roles and the role change are decided by the permission that the requester receives in X-User-Permission
 * and never by the name of a role, every role change is audited in MS08 (RNF-MS01-10) and records the session cut of
 * each modified user (RF-MS01-11)
 */
@Service
public class RoleServiceImpl implements RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleServiceImpl.class);

    static final String SELF_MODIFICATION_MESSAGE = "No puede modificar sus propios roles.";
    static final String LAST_ROLE_MESSAGE = "Cada usuario debe conservar al menos un rol.";
    static final String MAX_ROLES_MESSAGE = "Un usuario no puede tener más de "
            + GlobalValues.MAX_ROLES_PER_USER + " roles.";

    static final String REASON_NOT_ALLOWED = "SIN_PERMISO";
    static final String REASON_SELF_MODIFICATION = "AUTOMODIFICACION_DE_ROLES";
    static final String REASON_USER_NOT_FOUND = "USUARIO_NO_ENCONTRADO";
    static final String REASON_WITHOUT_ROLES = "USUARIO_SIN_ROLES";
    static final String REASON_TOO_MANY_ROLES = "MAXIMO_DE_ROLES_POR_USUARIO";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditRecorder auditRecorder;
    private final DatabaseTimeService timeService;

        /**
     * Creates the service with its dependencies
     *
     * @param userRepository repository to access the users and their roles
     * @param roleRepository repository to access the roles and their permissions
     * @param auditRecorder recorder of the audit events of MS08
     * @param timeService single database clock with which the session cuts are set
     */
    public RoleServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
            AuditRecorder auditRecorder, DatabaseTimeService timeService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditRecorder = auditRecorder;
        this.timeService = timeService;
    }

        /**
     * Queries the roles with their permissions, whoever has the permission to read all the roles receives the complete
     * catalog and the rest receive only their own roles
     *
     * @param requester identity of whoever queries
     * @return the roles, each one with the codes of its permissions
     */
    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionsResponse> getRoles(RequesterIdentity requester) {
        List<RoleEntity> visibleRoles;
        if (requester.hasPermission(Permission.MS01_ROLE_READ_ALL)) {
            visibleRoles = roleRepository.findAllByOrderByNameAsc();
        } else {
            visibleRoles = roleRepository.findAllByNameIn(requester.roles()).stream()
                    .sorted(Comparator.comparing(RoleEntity::getName))
                    .toList();
        }
        return visibleRoles.stream()
                .map(role -> new RolePermissionsResponse(role.getName(), PermissionCodes.ofRole(role)))
                .toList();
    }

        /**
     * Adds and removes roles to one or several users in a single atomic operation, every processed user is audited in
     * MS08
     *
     * @param requester identity of whoever requests
     * @param request body with the target users and the roles to add and to remove
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the final roles of each user
     * @throws ForbiddenOperationException if the requester does not have the required permission or includes themselves
     * @throws InvalidRequestException if the body is invalid, any role does not exist or any user would be left without
     *         roles
     * @throws BusinessRuleException if any user would be left with more roles than the maximum allowed
     * @throws UsersNotFoundException if any target user does not exist
     */
    @Override
    @Transactional
    public ChangeRolesResponse changeRoles(RequesterIdentity requester, ChangeRolesRequest request,
            String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        if (!requester.hasPermission(Permission.MS01_USER_ROLES)) {
            publishFailure(requester, null, REASON_NOT_ALLOWED, corr);
            log.warn("Role change rejected: the requester {} does not have the required permission",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }

        ChangeRolesCommand command = ChangeRolesValidator.validate(request);
        Map<String, RoleEntity> referencedRoles = verifyRolesExist(command);

        if (command.userIds().contains(requester.userId())) {
            publishFailure(requester, requester.userId(), REASON_SELF_MODIFICATION, corr);
            log.warn("Role change rejected: the user {} tries to modify their own roles",
                    requester.userId());
            throw new ForbiddenOperationException(SELF_MODIFICATION_MESSAGE);
        }

        List<UserEntity> users = userRepository.findAllByIdInOrderById(command.userIds());
        verifyAllUsersExist(requester, command, users, corr);

        Map<UUID, RoleChange> changes = planChanges(users, command);
        verifyNobodyLosesAllRoles(requester, command, changes, corr);
        verifyNobodyExceedsMaxRoles(requester, command, changes, corr);

        Instant cut = timeService.now();
        List<UserEntity> modified = applyChanges(changes, referencedRoles, cut);
        if (!modified.isEmpty()) {
            userRepository.saveAllAndFlush(modified);
        }

        List<ChangeRolesResponse.UserRoles> results = new ArrayList<>();
        for (UUID id : command.userIds()) {
            RoleChange change = changes.get(id);
            publishSuccess(requester, change, corr);
            results.add(new ChangeRolesResponse.UserRoles(id.toString(), RoleNames.sorted(change.after())));
        }
        log.info("Roles updated by user {}: {} user(s) processed, {} modified",
                requester.userId(), results.size(), modified.size());
        return new ChangeRolesResponse(results);
    }

        /**
     * Checks that all the roles to add and to remove exist in the roles table, if any does not exist the request is
     * rejected with 400 without auditing, like the other validation errors
     *
     * @param command validated request with the roles to add and to remove
     * @return the roles named in the request, by name
     * @throws InvalidRequestException if any role does not exist, with one error per list
     */
    private Map<String, RoleEntity> verifyRolesExist(ChangeRolesCommand command) {
        Set<String> referenced = new HashSet<>(command.rolesToAdd());
        referenced.addAll(command.rolesToRemove());

        Map<String, RoleEntity> found = new HashMap<>();
        if (!referenced.isEmpty()) {
            for (RoleEntity role : roleRepository.findAllByNameIn(referenced)) {
                found.put(role.getName(), role);
            }
        }

        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();
        if (!found.keySet().containsAll(command.rolesToAdd())) {
            errors.add(new ErrorResponse.ErrorDetail(ChangeRolesValidator.FIELD_ADD,
                    ChangeRolesValidator.INVALID_ROLES_MESSAGE));
        }
        if (!found.keySet().containsAll(command.rolesToRemove())) {
            errors.add(new ErrorResponse.ErrorDetail(ChangeRolesValidator.FIELD_REMOVE,
                    ChangeRolesValidator.INVALID_ROLES_MESSAGE));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        return found;
    }

        /**
     * Checks that all the requested ids correspond to an existing user, if any is missing, it audits a FALLO for each
     * non-existent id and throws 404 without having changed anything
     *
     * @param requester identity of whoever requests
     * @param command validated request with the requested ids
     * @param users users found in the database
     * @param corr correlationId
     * @throws UsersNotFoundException if any requested id does not correspond to a user
     */
    private void verifyAllUsersExist(RequesterIdentity requester, ChangeRolesCommand command,
            List<UserEntity> users, String corr) {
        Set<UUID> found = new LinkedHashSet<>();
        for (UserEntity user : users) {
            found.add(user.getId());
        }

        List<UUID> missing = new ArrayList<>();
        for (UUID id : command.userIds()) {
            if (!found.contains(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        for (UUID id : missing) {
            publishFailure(requester, id, REASON_USER_NOT_FOUND, corr);
        }
        log.warn("Role change rejected: {} non-existent user(s)", missing.size());
        throw new UsersNotFoundException(missing);
    }

        /**
     * Returns the roles that each user would have after the change
     *
     * @param users users that are going to be modified
     * @param command validated request with the roles to add and to remove
     * @return the final change of each user
     */
    private Map<UUID, RoleChange> planChanges(List<UserEntity> users, ChangeRolesCommand command) {
        Map<UUID, RoleChange> changes = new LinkedHashMap<>();
        for (UserEntity user : users) {
            Set<String> before = new HashSet<>(user.getRoleNames());

            Set<String> after = new HashSet<>(before);
            after.addAll(command.rolesToAdd());
            after.removeAll(command.rolesToRemove());

            changes.put(user.getId(), new RoleChange(user, before, after));
        }
        return changes;
    }

        /**
     * Checks that no user is left without roles. If any is left without roles, a failure is audited for each one and
     * 400 is thrown
     *
     * @param requester identity of whoever requests
     * @param command validated request with the roles to add and to remove
     * @param changes planned change of each user
     * @param corr correlationId
     * @throws InvalidRequestException if any user would be left without any role
     */
    private void verifyNobodyLosesAllRoles(RequesterIdentity requester, ChangeRolesCommand command,
            Map<UUID, RoleChange> changes, String corr) {
        boolean anyEmpty = false;
        for (UUID id : command.userIds()) {
            RoleChange change = changes.get(id);
            if (change.after().isEmpty()) {
                anyEmpty = true;
                publishFailure(requester, id, REASON_WITHOUT_ROLES, corr);
            }
        }
        if (anyEmpty) {
            log.warn("Role change rejected: some user would be left without roles");
            throw new InvalidRequestException(LAST_ROLE_MESSAGE, List.of());
        }
    }

        /**
     * Checks that no user is left with more roles than the maximum allowed. If any exceeds it, a failure is audited for
     * each one and the request is rejected with 422 without changing anything
     *
     * @param requester identity of whoever requests
     * @param command validated request with the target users
     * @param changes planned change of each user
     * @param corr correlationId
     * @throws BusinessRuleException if any user would be left with more roles than the maximum allowed
     */
    private void verifyNobodyExceedsMaxRoles(RequesterIdentity requester, ChangeRolesCommand command,
            Map<UUID, RoleChange> changes, String corr) {
        boolean anyExceeds = false;
        for (UUID id : command.userIds()) {
            RoleChange change = changes.get(id);
            if (change.after().size() > GlobalValues.MAX_ROLES_PER_USER) {
                anyExceeds = true;
                publishFailure(requester, id, REASON_TOO_MANY_ROLES, corr);
            }
        }
        if (anyExceeds) {
            log.warn("Role change rejected: some user would be left with more than {} roles",
                    GlobalValues.MAX_ROLES_PER_USER);
            throw new BusinessRuleException(MAX_ROLES_MESSAGE);
        }
    }

        /**
     * Assigns the new roles to the users and records their session cut, if they already had them no changes are made
     *
     * @param changes planned change of each user
     * @param referencedRoles roles named in the request, by name
     * @param cut instant of the session cut of each modified user, truncated to seconds
     * @return the users with the new roles
     */
    private List<UserEntity> applyChanges(Map<UUID, RoleChange> changes, Map<String, RoleEntity> referencedRoles,
            Instant cut) {
        List<UserEntity> modified = new ArrayList<>();
        for (RoleChange change : changes.values()) {
            if (!change.before().equals(change.after())) {
                Map<String, RoleEntity> byName = new HashMap<>(referencedRoles);
                for (RoleEntity current : change.user().getRoles()) {
                    byName.put(current.getName(), current);
                }
                Set<RoleEntity> finalRoles = new HashSet<>();
                for (String name : change.after()) {
                    finalRoles.add(byName.get(name));
                }
                change.user().setRoles(finalRoles);
                change.user().setValidFrom(cut);
                modified.add(change.user());
            }
        }
        return modified;
    }

        /**
     * Audits in MS08 the successful role change of a user, the detail lists the roles that were actually added and
     * removed
     *
     * @param requester identity of whoever made the change
     * @param change change applied to the user
     * @param corr correlation id of the request
     */
    private void publishSuccess(RequesterIdentity requester, RoleChange change, String corr) {
        Set<String> added = new HashSet<>(change.after());
        added.removeAll(change.before());

        Set<String> removed = new HashSet<>(change.before());
        removed.removeAll(change.after());

        String detail = "agregados=" + String.join(",", RoleNames.sorted(added)) + ";retirados="
                + String.join(",", RoleNames.sorted(removed));
        auditRecorder.recordAs(AuditAction.CAMBIO_ROL, AuditResult.EXITO, requester,
                AuditRecorder.userResource(change.user().getId()), detail, corr);
    }

        /**
     * Audits in MS08 a rejected role change
     *
     * @param requester identity of whoever made the request
     * @param targetId affected user, null if the rejection does not correspond to a specific user
     * @param reason reason for the rejection
     * @param corr correlation id of the request
     */
    private void publishFailure(RequesterIdentity requester, UUID targetId, String reason, String corr) {
        auditRecorder.recordAs(AuditAction.CAMBIO_ROL, AuditResult.FALLO, requester,
                AuditRecorder.userResource(targetId), reason, corr);
    }

        /**
     * Change requested for a user
     *
     * @param user user
     * @param before roles that it currently has
     * @param after roles that it would have after applying the change
     */
    private record RoleChange(UserEntity user, Set<String> before, Set<String> after) {
    }
}
