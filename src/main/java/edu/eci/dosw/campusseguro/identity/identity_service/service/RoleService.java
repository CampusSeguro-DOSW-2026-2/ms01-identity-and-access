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
 * Query of roles with their permissions and role change of the users (RF-MS01-05)
 */
public interface RoleService {

        /**
     * Queries the roles with their permissions, whoever has the permission to read all the roles receives the complete
     * catalog and the rest receive only their own roles
     *
     * @param requester identity of whoever queries
     * @return the roles visible to the requester, each one with the codes of its permissions
     */
    List<RolePermissionsResponse> getRoles(RequesterIdentity requester);

        /**
     * Adds and removes roles to one or several users in a single atomic operation, every processed user is audited in
     * MS08
     *
     * @param requester identity of whoever requests
     * @param request body with the users to modify and the roles to add and to remove
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the final roles of each user
     * @throws ForbiddenOperationException if the requester does not have the required permission or includes themselves
     * @throws InvalidRequestException if the body is invalid, any role does not exist or any user would be left without
     *         roles
     * @throws UsersNotFoundException if any target user does not exist
     */
    ChangeRolesResponse changeRoles(RequesterIdentity requester, ChangeRolesRequest request, String correlationId);
}
