package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RoleDetailResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ConflictException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.RoleNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Administración de roles y del catálogo de permisos (RF-MS01-10): consultar, crear, renombrar o describir,
 * cambiar los permisos y borrar roles
 */
public interface RoleAdminService {

    /**
     * Lista todos los roles con su detalle, ordenados por nombre
     *
     * @param requester identidad de quien consulta
     * @return los roles con su descripción, sus permisos y la marca de sistema
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de consulta de roles
     */
    List<RoleDetailResponse> listRoles(RequesterIdentity requester);

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
    RoleDetailResponse getRole(RequesterIdentity requester, String role);

    /**
     * Lista el catálogo de permisos ordenado por código
     *
     * @param requester identidad de quien consulta
     * @return cada permiso con su descripción
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso de consulta de roles
     */
    List<PermissionItemResponse> listPermissions(RequesterIdentity requester);

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
    RoleDetailResponse createRole(RequesterIdentity requester, CreateRoleRequest request, String correlationId);

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
    RoleDetailResponse updateRole(RequesterIdentity requester, String role, UpdateRoleRequest request,
            String correlationId);

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
    RoleDetailResponse updatePermissions(RequesterIdentity requester, String role,
            UpdateRolePermissionsRequest request, String correlationId);

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
    void deleteRole(RequesterIdentity requester, String role, String correlationId);
}
