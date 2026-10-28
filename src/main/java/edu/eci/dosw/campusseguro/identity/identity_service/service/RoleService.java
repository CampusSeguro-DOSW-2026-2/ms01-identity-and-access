package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Consulta de roles con sus permisos y cambio de roles de los usuarios (RF-MS01-05)
 */
public interface RoleService {

    /**
     * Consulta los roles con sus permisos, quien tenga el permiso de lectura de todos los roles recibe el
     * catálogo completo y los demás reciben solo sus propios roles
     *
     * @param requester identidad de quien consulta
     * @return los roles visibles para el solicitante, cada uno con los códigos de sus permisos
     */
    List<RolePermissionsResponse> getRoles(RequesterIdentity requester);

    /**
     * Agrega y retira roles a uno o varios usuarios en una sola operación atómica, cada usuario
     * procesado se audita en MS08
     *
     * @param requester identidad de quien solicita
     * @param request cuerpo con los usuarios a modificar y los roles a agregar y a retirar
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @return los roles finales de cada usuario
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido o se incluye a sí
     *         mismo
     * @throws InvalidRequestException si el cuerpo es inválido o algún usuario quedaría sin roles
     * @throws UsersNotFoundException si algún usuario objetivo no existe
     */
    ChangeRolesResponse changeRoles(RequesterIdentity requester, ChangeRolesRequest request, String correlationId);
}
