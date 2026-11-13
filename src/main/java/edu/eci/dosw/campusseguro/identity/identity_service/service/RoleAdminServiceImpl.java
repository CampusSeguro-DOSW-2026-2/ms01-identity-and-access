package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RoleDetailResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ConflictException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.RoleNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.RoleRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.UserRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.security.PermissionChecker;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.util.PermissionCodes;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;

/**
 * Administración de roles y del catálogo de permisos (RF-MS01-10)
 *
 * La autorización la decide el permiso que recibe el solicitante en X-User-Permission: ms01:role:read_all
 * para consultar y ms01:role:manage para crear, editar, cambiar permisos y borrar
 *
 * Cada operación de escritura se audita en MS08 con su resultado (RNF-MS01-10) y los cambios que afectan las
 * sesiones registran el corte en la misma transacción (RF-MS01-11): renombrar un rol corta a los usuarios que
 * lo tienen, cambiar sus permisos corta al rol y borrarlo corta a cada usuario afectado
 */
@Service
public class RoleAdminServiceImpl implements RoleAdminService {

    private static final Logger log = LoggerFactory.getLogger(RoleAdminServiceImpl.class);

    static final String NAME_EXISTS_MESSAGE = "Ya existe un rol con ese nombre.";
    static final String SYSTEM_RENAME_MESSAGE = "Los roles de sistema no se pueden renombrar.";
    static final String SYSTEM_DELETE_MESSAGE = "Los roles de sistema no se pueden eliminar.";
    static final String MANAGE_EXCLUSIVE_MESSAGE = "El permiso ms01:role:manage es exclusivo del rol ADMIN.";
    static final String ADMIN_MUST_KEEP_MESSAGE = "El rol ADMIN debe conservar el permiso ms01:role:manage.";

    static final String REASON_NOT_ALLOWED = "SIN_PERMISO";
    static final String REASON_VALIDATION = "VALIDACION";
    static final String REASON_ROLE_NOT_FOUND = "ROL_NO_ENCONTRADO";
    static final String REASON_SYSTEM_ROLE = "ROL_DE_SISTEMA";
    static final String REASON_CONFLICT = "CONFLICTO";
    static final String REASON_BUSINESS_RULE = "REGLA_NEGOCIO";
    static final String REASON_INTERNAL = "ERROR_INTERNO";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;
    private final DatabaseTimeService timeService;

    /**
     * Crea el servicio con sus dependencias
     *
     * @param roleRepository repositorio de acceso a los roles y sus permisos
     * @param userRepository repositorio de acceso a los usuarios y sus roles
     * @param auditRecorder registrador de los eventos de auditoría de MS08
     * @param timeService reloj único de la base de datos con el que se fijan los cortes de sesión
     */
    public RoleAdminServiceImpl(RoleRepository roleRepository, UserRepository userRepository,
            AuditRecorder auditRecorder, DatabaseTimeService timeService) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
        this.timeService = timeService;
    }

    /**
     * Lista todos los roles con su detalle, ordenados por nombre
     *
     * @param requester identidad de quien consulta
     * @return los roles con su descripción, sus permisos y la marca de sistema
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de consulta de roles
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
     * Consulta un rol por su nombre
     *
     * @param requester identidad de quien consulta
     * @param role nombre del rol recibido en la ruta, se normaliza
     * @return el detalle del rol
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de consulta de roles
     * @throws InvalidRequestException si el nombre no tiene un formato válido
     * @throws RoleNotFoundException si el rol no existe
     */
    @Override
    @Transactional(readOnly = true)
    public RoleDetailResponse getRole(RequesterIdentity requester, String role) {
        PermissionChecker.require(requester, Permission.MS01_ROLE_READ_ALL);
        String name = RoleRequestValidator.normalizePathRole(role);
        RoleEntity entity = roleRepository.findById(name)
                .orElseThrow(() -> new RoleNotFoundException(name));
        return toDetail(entity);
    }

    /**
     * Lista el catálogo de permisos ordenado por código
     *
     * @param requester identidad de quien consulta
     * @return cada permiso con su descripción
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de consulta de roles
     */
    @Override
    public List<PermissionItemResponse> listPermissions(RequesterIdentity requester) {
        PermissionChecker.require(requester, Permission.MS01_ROLE_READ_ALL);
        return Permission.sortedByCode().stream()
                .map(permission -> new PermissionItemResponse(permission.getCode(), permission.getDescription()))
                .toList();
    }

    /**
     * Crea un rol que no es de sistema y sin corte de sesión (formulario F-10), el intento se audita en MS08
     *
     * @param requester identidad de quien solicita
     * @param request nombre, descripción y permisos del rol
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return el rol creado
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de gestión de roles
     * @throws InvalidRequestException si algún dato incumple el formulario F-10
     * @throws BusinessRuleException si los permisos incluyen ms01:role:manage
     * @throws ConflictException si ya existe un rol con ese nombre
     */
    @Override
    @Transactional
    public RoleDetailResponse createRole(RequesterIdentity requester, CreateRoleRequest request,
            String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);
        requireManage(AuditAction.CREACION_ROL, requester, corr);

        CreateRoleCommand command;
        try {
            command = RoleRequestValidator.validateCreate(request);
        } catch (InvalidRequestException e) {
            reject(AuditAction.CREACION_ROL, requester, RoleNames.normalize(request.getRol()), REASON_VALIDATION,
                    corr);
            throw e;
        }
        if (command.permissions().contains(Permission.MS01_ROLE_MANAGE.getCode())) {
            reject(AuditAction.CREACION_ROL, requester, command.name(), REASON_BUSINESS_RULE, corr);
            throw new BusinessRuleException(MANAGE_EXCLUSIVE_MESSAGE);
        }
        if (roleRepository.existsById(command.name())) {
            reject(AuditAction.CREACION_ROL, requester, command.name(), REASON_CONFLICT, corr);
            throw new ConflictException(NAME_EXISTS_MESSAGE);
        }

        RoleEntity entity = new RoleEntity();
        entity.setName(command.name());
        entity.setDescription(command.description());
        entity.setPermissions(command.permissions());
        entity.setSystem(false);

        RoleEntity saved;
        try {
            saved = roleRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            reject(AuditAction.CREACION_ROL, requester, command.name(), REASON_CONFLICT, corr);
            log.warn("Creación de rol rechazada por restricción de unicidad: {}", e.getClass().getSimpleName());
            throw new ConflictException(NAME_EXISTS_MESSAGE);
        } catch (RuntimeException e) {
            reject(AuditAction.CREACION_ROL, requester, command.name(), REASON_INTERNAL, corr);
            log.error("Creación de rol fallida: no fue posible guardar el rol {}", command.name(), e);
            throw e;
        }

        String detail = "descripcion=" + command.description() + ";permisos=" + String.join(",", command.permissions());
        auditRecorder.recordAs(AuditAction.CREACION_ROL, AuditResult.EXITO, requester,
                AuditRecorder.roleResource(command.name()), detail, corr);
        log.info("Rol creado por el usuario {}: {}", requester.userId(), command.name());
        return toDetail(saved);
    }

    /**
     * Cambia el nombre y la descripción de un rol (formulario F-11), si el nombre cambia registra el corte de
     * sesión de los usuarios que tienen el rol, el intento se audita en MS08
     *
     * @param requester identidad de quien solicita
     * @param role nombre actual del rol recibido en la ruta, se normaliza
     * @param request nombre y descripción nuevos
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return el rol actualizado
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de gestión de roles
     * @throws InvalidRequestException si algún dato incumple el formulario F-11
     * @throws RoleNotFoundException si el rol no existe
     * @throws ConflictException si es un rol de sistema que cambia de nombre o el nombre nuevo ya existe
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
        if (renamed && roleRepository.existsById(command.name())) {
            reject(AuditAction.EDICION_ROL, requester, previousName, REASON_CONFLICT, corr);
            throw new ConflictException(NAME_EXISTS_MESSAGE);
        }

        RoleEntity updated;
        try {
            if (renamed) {
                Instant cut = timeService.now();
                userRepository.markSessionCutByRole(previousName, cut);
                roleRepository.rename(previousName, command.name(), command.description(), cut);
                updated = roleRepository.findById(command.name()).orElseThrow();
            } else {
                current.setDescription(command.description());
                updated = roleRepository.saveAndFlush(current);
            }
        } catch (DataIntegrityViolationException e) {
            reject(AuditAction.EDICION_ROL, requester, previousName, REASON_CONFLICT, corr);
            log.warn("Edición de rol rechazada por restricción de la base de datos: {}",
                    e.getClass().getSimpleName());
            throw new ConflictException(NAME_EXISTS_MESSAGE);
        } catch (RuntimeException e) {
            reject(AuditAction.EDICION_ROL, requester, previousName, REASON_INTERNAL, corr);
            log.error("Edición de rol fallida: no fue posible guardar el rol {}", previousName, e);
            throw e;
        }

        String detail = "nombre_anterior=" + previousName + ";descripcion_anterior=" + previousDescription
                + ";nombre_nuevo=" + updated.getName() + ";descripcion_nueva=" + updated.getDescription();
        auditRecorder.recordAs(AuditAction.EDICION_ROL, AuditResult.EXITO, requester,
                AuditRecorder.roleResource(updated.getName()), detail, corr);
        log.info("Rol editado por el usuario {}: {}", requester.userId(), updated.getName());
        return toDetail(updated);
    }

    /**
     * Reemplaza los permisos de un rol (formulario F-12) y registra el corte de sesión del rol, el intento se
     * audita en MS08
     *
     * @param requester identidad de quien solicita
     * @param role nombre del rol recibido en la ruta, se normaliza
     * @param request lista completa de permisos del rol
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return el rol actualizado
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de gestión de roles
     * @throws InvalidRequestException si algún dato incumple el formulario F-12
     * @throws RoleNotFoundException si el rol no existe
     * @throws BusinessRuleException si ms01:role:manage queda en un rol distinto de ADMIN o ADMIN lo pierde
     */
    @Override
    @Transactional
    public RoleDetailResponse updatePermissions(RequesterIdentity requester, String role,
            UpdateRolePermissionsRequest request, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);
        requireManage(AuditAction.CAMBIO_PERMISOS_ROL, requester, corr);
        RoleEntity entity = findRole(AuditAction.CAMBIO_PERMISOS_ROL, requester, role, corr);

        RolePermissionsCommand command;
        try {
            command = RoleRequestValidator.validatePermissions(request);
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

        Set<String> before = new HashSet<>(entity.getPermissions());
        Set<String> after = new HashSet<>(command.permissions());
        Set<String> added = new HashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(after);

        RoleEntity saved;
        try {
            entity.setPermissions(command.permissions());
            entity.setValidFrom(timeService.now());
            saved = roleRepository.saveAndFlush(entity);
        } catch (RuntimeException e) {
            reject(AuditAction.CAMBIO_PERMISOS_ROL, requester, entity.getName(), REASON_INTERNAL, corr);
            log.error("Cambio de permisos fallido: no fue posible guardar el rol {}", entity.getName(), e);
            throw e;
        }

        String detail = "agregados=" + String.join(",", RoleNames.sorted(added)) + ";retirados="
                + String.join(",", RoleNames.sorted(removed));
        auditRecorder.recordAs(AuditAction.CAMBIO_PERMISOS_ROL, AuditResult.EXITO, requester,
                AuditRecorder.roleResource(saved.getName()), detail, corr);
        log.info("Permisos del rol {} cambiados por el usuario {}", saved.getName(), requester.userId());
        return toDetail(saved);
    }

    /**
     * Borra un rol (formulario F-13): quienes tienen solo ese rol pasan a ESTUDIANTE, a los demás se les retira
     * el rol y se registra el corte de sesión de cada uno, todo en una sola transacción, el intento se audita
     * en MS08
     *
     * @param requester identidad de quien solicita
     * @param role nombre del rol recibido en la ruta, se normaliza
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de gestión de roles
     * @throws InvalidRequestException si el nombre no tiene un formato válido
     * @throws RoleNotFoundException si el rol no existe
     * @throws ConflictException si el rol es de sistema
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
            roleRepository.deleteById(name);
            roleRepository.flush();
        } catch (RuntimeException e) {
            reject(AuditAction.ELIMINACION_ROL, requester, name, REASON_INTERNAL, corr);
            log.error("Eliminación de rol fallida: no fue posible borrar el rol {}", name, e);
            throw e;
        }

        String detail = "usuarios_a_estudiante=" + movedToStudent + ";usuarios_que_solo_perdieron_el_rol="
                + onlyLostRole;
        auditRecorder.recordAs(AuditAction.ELIMINACION_ROL, AuditResult.EXITO, requester,
                AuditRecorder.roleResource(name), detail, corr);
        log.info("Rol {} eliminado por el usuario {}: {} usuario(s) pasaron a {} y {} perdieron el rol", name,
                requester.userId(), movedToStudent, RoleNames.STUDENT, onlyLostRole);
    }

    /**
     * Exige el permiso de gestión de roles, si falta audita el rechazo y responde 403
     *
     * @param action acción de auditoría de la operación
     * @param requester identidad de quien solicita
     * @param corr correlation id de la petición
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de gestión de roles
     */
    private void requireManage(AuditAction action, RequesterIdentity requester, String corr) {
        if (!requester.hasPermission(Permission.MS01_ROLE_MANAGE)) {
            reject(action, requester, null, REASON_NOT_ALLOWED, corr);
            log.warn("Operación sobre roles rechazada: el solicitante {} no tiene el permiso de gestión de roles",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }
    }

    /**
     * Normaliza el nombre de la ruta y busca el rol, si el formato no es válido o el rol no existe audita el
     * rechazo y lanza el error
     *
     * @param action acción de auditoría de la operación
     * @param requester identidad de quien solicita
     * @param rawRole nombre del rol recibido en la ruta
     * @param corr correlation id de la petición
     * @return el rol encontrado
     * @throws InvalidRequestException si el nombre no tiene un formato válido
     * @throws RoleNotFoundException si el rol no existe
     */
    private RoleEntity findRole(AuditAction action, RequesterIdentity requester, String rawRole, String corr) {
        String name;
        try {
            name = RoleRequestValidator.normalizePathRole(rawRole);
        } catch (InvalidRequestException e) {
            reject(action, requester, null, REASON_VALIDATION, corr);
            throw e;
        }
        return roleRepository.findById(name).orElseThrow(() -> {
            reject(action, requester, name, REASON_ROLE_NOT_FOUND, corr);
            return new RoleNotFoundException(name);
        });
    }

    /**
     * Audita en MS08 el rechazo de una operación sobre roles
     *
     * @param action acción de auditoría de la operación
     * @param requester identidad de quien hizo la petición
     * @param roleName rol afectado, null si el rechazo no corresponde a un rol conocido
     * @param reason motivo del rechazo
     * @param corr correlation id de la petición
     */
    private void reject(AuditAction action, RequesterIdentity requester, String roleName, String reason,
            String corr) {
        auditRecorder.recordAs(action, AuditResult.FALLO, requester, AuditRecorder.roleResource(roleName), reason,
                corr);
    }

    /**
     * Convierte un rol guardado en su detalle para la API, con los permisos vigentes del catálogo
     *
     * @param role rol guardado
     * @return el detalle del rol
     */
    private static RoleDetailResponse toDetail(RoleEntity role) {
        return new RoleDetailResponse(role.getName(), role.getDescription(), PermissionCodes.ofRole(role),
                role.isSystem());
    }
}
