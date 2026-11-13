package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
 * Gestión de roles y permisos (RF-MS01-05)
 *
 * La consulta de roles y el cambio de roles los decide el permiso que recibe el solicitante en
 * X-User-Permission y nunca el nombre de un rol, cada cambio de roles se audita en MS08 (RNF-MS01-10) y
 * registra el corte de sesión de cada usuario modificado (RF-MS01-11)
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
     * Crea el servicio con sus dependencias
     *
     * @param userRepository repositorio de acceso a los usuarios y sus roles
     * @param roleRepository repositorio de acceso a los roles y sus permisos
     * @param auditRecorder registrador de los eventos de auditoría de MS08
     * @param timeService reloj único de la base de datos con el que se fijan los cortes de sesión
     */
    public RoleServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
            AuditRecorder auditRecorder, DatabaseTimeService timeService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditRecorder = auditRecorder;
        this.timeService = timeService;
    }

    /**
     * Consulta los roles con sus permisos, quien tenga el permiso de lectura de todos los roles recibe
     * el catálogo completo y los demás reciben solo sus propios roles
     *
     * @param requester identidad de quien consulta
     * @return los roles, cada uno con los códigos de sus permisos
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
     * Agrega y retira roles a uno o varios usuarios en una sola operación atómica, cada usuario
     * procesado se audita en MS08
     *
     * @param requester identidad de quien solicita
     * @param request cuerpo con los usuarios objetivo y los roles a agregar y a retirar
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return los roles finales de cada usuario
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido o se incluye a sí
     *         mismo
     * @throws InvalidRequestException si el cuerpo es inválido, algún rol no existe o algún usuario quedaría
     *         sin roles
     * @throws BusinessRuleException si algún usuario quedaría con más roles que el máximo permitido
     * @throws UsersNotFoundException si algún usuario objetivo no existe
     */
    @Override
    @Transactional
    public ChangeRolesResponse changeRoles(RequesterIdentity requester, ChangeRolesRequest request,
            String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);

        if (!requester.hasPermission(Permission.MS01_USER_ROLES)) {
            publishFailure(requester, null, REASON_NOT_ALLOWED, corr);
            log.warn("Cambio de roles rechazado: el solicitante {} no tiene el permiso requerido",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }

        ChangeRolesCommand command = ChangeRolesValidator.validate(request);
        verifyRolesExist(command);

        if (command.userIds().contains(requester.userId())) {
            publishFailure(requester, requester.userId(), REASON_SELF_MODIFICATION, corr);
            log.warn("Cambio de roles rechazado: el usuario {} intenta modificar sus propios roles",
                    requester.userId());
            throw new ForbiddenOperationException(SELF_MODIFICATION_MESSAGE);
        }

        List<UserEntity> users = userRepository.findAllByIdInOrderById(command.userIds());
        verifyAllUsersExist(requester, command, users, corr);

        Map<UUID, RoleChange> changes = planChanges(users, command);
        verifyNobodyLosesAllRoles(requester, command, changes, corr);
        verifyNobodyExceedsMaxRoles(requester, command, changes, corr);

        Instant cut = timeService.now();
        List<UserEntity> modified = applyChanges(changes, cut);
        if (!modified.isEmpty()) {
            userRepository.saveAllAndFlush(modified);
        }

        List<ChangeRolesResponse.UserRoles> results = new ArrayList<>();
        for (UUID id : command.userIds()) {
            RoleChange change = changes.get(id);
            publishSuccess(requester, change, corr);
            results.add(new ChangeRolesResponse.UserRoles(id.toString(), RoleNames.sorted(change.after())));
        }
        log.info("Roles actualizados por el usuario {}: {} usuario(s) procesado(s), {} modificado(s)",
                requester.userId(), results.size(), modified.size());
        return new ChangeRolesResponse(results);
    }

    /**
     * Comprueba que todos los roles a agregar y a retirar existan en la tabla roles, si alguno no existe se
     * rechaza la petición con 400 sin auditar, igual que los demás errores de validación
     *
     * @param command petición validada con los roles a agregar y a retirar
     * @throws InvalidRequestException si algún rol no existe, con un error por lista
     */
    private void verifyRolesExist(ChangeRolesCommand command) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();
        if (!command.rolesToAdd().isEmpty()
                && roleRepository.countByNameIn(command.rolesToAdd()) != command.rolesToAdd().size()) {
            errors.add(new ErrorResponse.ErrorDetail(ChangeRolesValidator.FIELD_ADD,
                    ChangeRolesValidator.INVALID_ROLES_MESSAGE));
        }
        if (!command.rolesToRemove().isEmpty()
                && roleRepository.countByNameIn(command.rolesToRemove()) != command.rolesToRemove().size()) {
            errors.add(new ErrorResponse.ErrorDetail(ChangeRolesValidator.FIELD_REMOVE,
                    ChangeRolesValidator.INVALID_ROLES_MESSAGE));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
    }

    /**
     * Comprueba que todos los ids pedidos correspondan a un usuario existente, si falta alguno,
     * audita un FALLO por cada id inexistente y lanza 404 sin haber cambiado nada
     *
     * @param requester identidad de quien solicita
     * @param command peticion validada con los ids pedidos
     * @param users usuarios encontrados en la base de datos
     * @param corr correlationId
     * @throws UsersNotFoundException si algun id pedido no corresponde a un usuario
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
        log.warn("Cambio de roles rechazado: {} usuario(s) inexistente(s)", missing.size());
        throw new UsersNotFoundException(missing);
    }

    /**
     * Devuelve los roles que tendria cada usuario despues del cambio
     *
     * @param users usuarios que se van a modificar
     * @param command peticion validada con los roles a agregar y a retirar
     * @return el cambio final de cada usuario
     */
    private Map<UUID, RoleChange> planChanges(List<UserEntity> users, ChangeRolesCommand command) {
        Map<UUID, RoleChange> changes = new LinkedHashMap<>();
        for (UserEntity user : users) {
            Set<String> before = new HashSet<>();
            if (user.getRoles() != null) {
                before.addAll(user.getRoles());
            }

            Set<String> after = new HashSet<>(before);
            after.addAll(command.rolesToAdd());
            after.removeAll(command.rolesToRemove());

            changes.put(user.getId(), new RoleChange(user, before, after));
        }
        return changes;
    }

    /**
     * Comprueba que ningun usuario quede sin roles. Si alguno queda sin roles, se audita un fallo por
     * cada uno y lanza 400
     *
     * @param requester identidad de quien solicita
     * @param command peticion validada con los roles a agregar y a retirar
     * @param changes cambio previsto de cada usuario
     * @param corr correlationId
     * @throws InvalidRequestException si algun usuario quedaria sin ningun rol
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
            log.warn("Cambio de roles rechazado: algún usuario quedaría sin roles");
            throw new InvalidRequestException(LAST_ROLE_MESSAGE, List.of());
        }
    }

    /**
     * Comprueba que ningún usuario quede con más roles que el máximo permitido. Si alguno lo supera, se audita
     * un fallo por cada uno y se rechaza la petición con 422 sin cambiar nada
     *
     * @param requester identidad de quien solicita
     * @param command petición validada con los usuarios objetivo
     * @param changes cambio previsto de cada usuario
     * @param corr correlationId
     * @throws BusinessRuleException si algún usuario quedaría con más roles que el máximo permitido
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
            log.warn("Cambio de roles rechazado: algún usuario quedaría con más de {} roles",
                    GlobalValues.MAX_ROLES_PER_USER);
            throw new BusinessRuleException(MAX_ROLES_MESSAGE);
        }
    }

    /**
     * Asigna los roles nuevos a los usuarios y registra su corte de sesión, si ya los tenian no se hace
     * cambios
     *
     * @param changes cambio previsto de cada usuario
     * @param cut instante del corte de sesión de cada usuario modificado, truncado a segundos
     * @return los usuarios con los nuevos roles
     */
    private List<UserEntity> applyChanges(Map<UUID, RoleChange> changes, Instant cut) {
        List<UserEntity> modified = new ArrayList<>();
        for (RoleChange change : changes.values()) {
            if (!change.before().equals(change.after())) {
                change.user().setRoles(change.after());
                change.user().setValidFrom(cut);
                modified.add(change.user());
            }
        }
        return modified;
    }

    /**
     * Audita en MS08 el cambio de roles exitoso de un usuario, el detalle lista los roles que realmente
     * se agregaron y se retiraron
     *
     * @param requester identidad de quien realizó el cambio
     * @param change cambio aplicado al usuario
     * @param corr correlation id de la petición
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
     * Audita en MS08 un cambio de roles rechazado
     *
     * @param requester identidad de quien hizo la petición
     * @param targetId usuario afectado, null si el rechazo no corresponde a un usuario concreto
     * @param reason motivo del rechazo
     * @param corr correlation id de la petición
     */
    private void publishFailure(RequesterIdentity requester, UUID targetId, String reason, String corr) {
        auditRecorder.recordAs(AuditAction.CAMBIO_ROL, AuditResult.FALLO, requester,
                AuditRecorder.userResource(targetId), reason, corr);
    }

    /**
     * Cambio solicitado para un usuario
     *
     * @param user usuario
     * @param before roles que tiene actualmente
     * @param after roles que tendria al aplicar el cambio
     */
    private record RoleChange(UserEntity user, Set<String> before, Set<String> after) {
    }
}
