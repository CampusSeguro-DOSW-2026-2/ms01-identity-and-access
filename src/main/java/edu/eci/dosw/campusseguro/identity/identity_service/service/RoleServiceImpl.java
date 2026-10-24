package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * RF-MS01-05 Gestion de roles y permisos.
 *
 * Consulta de roles, el ADMINISTRADOR recibe el catalogo completo y los demas solo sus propios roles
 *
 * Cambio de roles, solo lo hace el ADMINISTRADOR
 *
 */
@Service
public class RoleServiceImpl implements RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleServiceImpl.class);

    static final String FORBIDDEN_MESSAGE = "No tiene permisos para realizar esta operación.";
    static final String SELF_MODIFICATION_MESSAGE = "No puede modificar sus propios roles.";
    static final String LAST_ROLE_MESSAGE = "Cada usuario debe conservar al menos un rol.";

    static final String REASON_NOT_ALLOWED = "SIN_PERMISO";
    static final String REASON_SELF_MODIFICATION = "AUTOMODIFICACION_DE_ROLES";
    static final String REASON_USER_NOT_FOUND = "USUARIO_NO_ENCONTRADO";
    static final String REASON_WITHOUT_ROLES = "USUARIO_SIN_ROLES";

    static final String RESOURCE_PREFIX = "users/";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[\\x21-\\x7E]{1,100}$");

    private final UserRepository userRepository;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    /**
     * Crea el servicio con sus dependencias.
     *
     * @param userRepository repositorio de sacceso a los usuarios y sus roles
     * @param auditPublisher auditoria de MS08
     * @param clock reloj UTC
     */
    public RoleServiceImpl(UserRepository userRepository, AuditEventPublisher auditPublisher, Clock clock) {
        this.userRepository = userRepository;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    /**
     * Consulta los roles con sus permisos, el que tenga el permiso ms01:role:read_all recibe todos
     * los roles, los demas reciben solo los roles propios.
     *
     * @param requester identidad de quien consulta
     * @return los roles cada uno con los codigos de sus permisos
     */
    @Override
    public List<RolePermissionsResponse> getRoles(RequesterIdentity requester) {
        Collection<UserRole> visibleRoles;
        if (requester.hasPermission(Permission.MS01_ROLE_READ_ALL)) {
            visibleRoles = EnumSet.allOf(UserRole.class);
        } else {
            visibleRoles = requester.roles();
        }
        return visibleRoles.stream()
                .map(RolePermissionsResponse::from)
                .toList();
    }

    /**
     * Agrega y retira roles a uno o varios usuarios
     *
     * @param requester identidad de quien solicita; debe tener el permiso ms01:user:roles
     * @param request cuerpo con los usuarios objetivo y los roles a agregar y a retirar
     * @param correlationId correlationId
     * @return los roles finales de cada usuario
     * @throws ForbiddenOperationException si el solicitante no es administrador o se incluye a si mismo
     * @throws InvalidRequestException si el cuerpo es invalido o algun usuario quedaria sin roles
     * @throws UsersNotFoundException si algun usuario objetivo no existe
     */
    @Override
    @Transactional
    public ChangeRolesResponse changeRoles(RequesterIdentity requester, ChangeRolesRequest request,
            String correlationId) {
        String corr = resolveCorrelationId(correlationId);

        if (!requester.hasPermission(Permission.MS01_USER_ROLES)) {
            publishFailure(requester.userId(), null, REASON_NOT_ALLOWED, corr);
            log.warn("[{}] Cambio de roles rechazado: el solicitante {} no es administrador.", corr,
                    requester.userId());
            throw new ForbiddenOperationException(FORBIDDEN_MESSAGE);
        }

        ChangeRolesCommand command = ChangeRolesValidator.validate(request);

        if (command.userIds().contains(requester.userId())) {
            publishFailure(requester.userId(), requester.userId(), REASON_SELF_MODIFICATION, corr);
            log.warn("[{}] Cambio de roles rechazado: el administrador {} intenta modificar sus propios roles.",
                    corr, requester.userId());
            throw new ForbiddenOperationException(SELF_MODIFICATION_MESSAGE);
        }

        List<UserEntity> users = userRepository.findAllByIdInOrderById(command.userIds());
        verifyAllUsersExist(requester, command, users, corr);

        Map<UUID, RoleChange> changes = planChanges(users, command);
        verifyNobodyLosesAllRoles(requester, command, changes, corr);

        List<UserEntity> modified = applyChanges(changes);
        if (!modified.isEmpty()) {
            userRepository.saveAllAndFlush(modified);
        }

        List<ChangeRolesResponse.UserRoles> results = new ArrayList<>();
        for (UUID id : command.userIds()) {
            RoleChange change = changes.get(id);
            publishSuccess(requester.userId(), change, corr);
            results.add(new ChangeRolesResponse.UserRoles(id.toString(), sorted(change.after())));
        }
        log.info("[{}] Roles actualizados por el administrador {}: {} usuario(s) procesado(s), {} modificado(s).",
                corr, requester.userId(), results.size(), modified.size());
        return new ChangeRolesResponse(results);
    }

    /**
     * Comprueba que todos los ids pedidos correspondan a un usuario existente, si falta alguno,
     * audita un FALLO por cada id inexistente y lanza 404 sin haber cambiado nada.
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
            publishFailure(requester.userId(), id, REASON_USER_NOT_FOUND, corr);
        }
        log.warn("[{}] Cambio de roles rechazado: {} usuario(s) inexistente(s).", corr, missing.size());
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
            Set<UserRole> before = EnumSet.noneOf(UserRole.class);
            if (user.getRoles() != null) {
                before.addAll(user.getRoles());
            }

            Set<UserRole> after = EnumSet.noneOf(UserRole.class);
            after.addAll(before);
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
                publishFailure(requester.userId(), id, REASON_WITHOUT_ROLES, corr);
            }
        }
        if (anyEmpty) {
            log.warn("[{}] Cambio de roles rechazado: algun usuario quedaria sin roles.", corr);
            throw new InvalidRequestException(LAST_ROLE_MESSAGE, List.of());
        }
    }

    /**
     * Asigna los roles nuevos a los usuarios, si ya los tenian no se hace cambios
     *
     * @param changes cambio previsto de cada usuario
     * @return los usuarios con los nuevos roles
     */
    private List<UserEntity> applyChanges(Map<UUID, RoleChange> changes) {
        List<UserEntity> modified = new ArrayList<>();
        for (RoleChange change : changes.values()) {
            if (!change.before().equals(change.after())) {
                change.user().setRoles(new ArrayList<>(sorted(change.after())));
                modified.add(change.user());
            }
        }
        return modified;
    }

    /**
     * Audita en MS08 el cambio de roles exitoso de un usuario
     *
     * @param requesterId administrador que realizo el cambio
     * @param change cambio aplicado al usuario
     * @param corr correlationId
     */
    private void publishSuccess(UUID requesterId, RoleChange change, String corr) {
        String detail = "rol_anterior=" + join(change.before()) + ";rol_nuevo=" + join(change.after());
        publish(AuditResult.EXITO, requesterId, change.user().getId(), detail, corr);
    }

    /**
     * Audita en MS08 un cambio de roles rechazado
     *
     * @param requesterId usuario que hizo la peticion
     * @param targetId usuario afectado
     * @param reason motivo del rechazo
     * @param corr correlationId
     */
    private void publishFailure(UUID requesterId, UUID targetId, String reason, String corr) {
        publish(AuditResult.FALLO, requesterId, targetId, reason, corr);
    }

    /**
     * Envia el evento CAMBIO_ROL al auditPublisher
     *
     * @param result EXITO o FALLO
     * @param requesterId usuario que hizo la peticion
     * @param targetId usuario afectado
     * @param detail roles anterior y nuevo en un EXITO, o el motivo de un FALLO
     * @param corr correlationId
     */
    private void publish(AuditResult result, UUID requesterId, UUID targetId, String detail, String corr) {
        String resource = null;
        if (targetId != null) {
            resource = RESOURCE_PREFIX + targetId;
        }
        try {
            auditPublisher.publish(new AuditEvent(AuditAction.CAMBIO_ROL, result, requesterId, null, detail,
                    corr, clock.instant(), resource));
        } catch (RuntimeException e) {
            log.error("[{}] Falló la emisión del evento de auditoría CAMBIO_ROL/{}: {}", corr, result,
                    e.toString());
        }
    }

    /**
     * Ordena alfabeticamente un conjunto de roles
     *
     * @param roles conjunto de roles
     * @return lista con los roles ordenados por nombre
     */
    private static List<UserRole> sorted(Set<UserRole> roles) {
        return roles.stream()
                .sorted(Comparator.comparing(UserRole::name))
                .toList();
    }

    /**
     * Une los nombres de un conjunto de roles en un texto separado por comas, en orden alfabetico
     *
     * @param roles conjunto de roles
     * @return nombres separados por coma
     */
    private static String join(Set<UserRole> roles) {
        List<String> names = new ArrayList<>();
        for (UserRole role : sorted(roles)) {
            names.add(role.name());
        }
        return String.join(",", names);
    }

    /**
     * Normaliza el X-Correlation-ID recibido, si es un valor imprimible de hasta 100 caracteres lo usa
     * recortado, si falta o no es valido genera uno aleatorio
     *
     * @param correlationId correlationId
     * @return el correlationId limpio o uno nuevo
     */
    private static String resolveCorrelationId(String correlationId) {
        if (correlationId != null) {
            String trimmed = correlationId.trim();
            if (SAFE_CORRELATION_ID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Cambio solicitado para un usuario
     *
     * @param user usuario
     * @param before roles que tiene actualmente
     * @param after roles que tendria al aplicar el cambio
     */
    private record RoleChange(UserEntity user, Set<UserRole> before, Set<UserRole> after) {
    }
}
