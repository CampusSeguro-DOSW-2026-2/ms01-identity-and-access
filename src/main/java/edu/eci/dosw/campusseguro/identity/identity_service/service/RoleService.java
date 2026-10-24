package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 *  Interfaz para la consulta de roles con sus permisos y cambio de roles de los usuarios por
 * parte del administrador (RF-MS01-05)
 */
public interface RoleService {

    /**
     * Consulta los roles con sus permisos, el que tenga el permiso ms01:role:read_all recibe todos los
     * roles, los demas reciben solo sus propios roles
     *
     * @param requester identidad de quien consulta
     * @return los roles visibles para el solicitante, cada uno con los codigos de sus permisos
     */
    List<RolePermissionsResponse> getRoles(RequesterIdentity requester);

    /**
     * Agrega y retira roles a uno o varios usuarios. Cada usuario procesado se audita en MS08 con la
     * accion CAMBIO_ROL
     *
     * @param requester identidad de quien solicita
     * @param request cuerpo con los usuarios a modificar y los roles a agregar y a retirar
     * @param correlationId correlationId
     * @return los roles finales de cada usuario
     */
    ChangeRolesResponse changeRoles(RequesterIdentity requester, ChangeRolesRequest request, String correlationId);
}
