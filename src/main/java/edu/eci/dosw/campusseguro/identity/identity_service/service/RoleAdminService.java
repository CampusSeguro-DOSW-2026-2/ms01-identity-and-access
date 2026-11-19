package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.AlertRecipientRoleResponse;
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
 * Administration of roles and of the permission catalog (RF-MS01-10): query, create, rename or describe, change the
 * permissions and delete roles
 */
public interface RoleAdminService {

        /**
     * Lists all the roles with their detail, sorted by name
     *
     * @param requester identity of whoever queries
     * @return the roles with their description, their permissions and the system flag
     * @throws ForbiddenOperationException if the requester does not have the role query permission
     */
    List<RoleDetailResponse> listRoles(RequesterIdentity requester);

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
    RoleDetailResponse getRole(RequesterIdentity requester, String role);

        /**
     * Lists the permission catalog sorted by code
     *
     * @param requester identity of whoever queries
     * @return each permission with its description
     * @throws ForbiddenOperationException if the requester does not have the role query permission
     */
    List<PermissionItemResponse> listPermissions(RequesterIdentity requester);

        /**
     * Lists the roles that can receive alerts (the ones with ms03:alert:receive), for the recipient picker of the
     * alerts of MS03. Whoever creates or edits alerts does not have ms01:role:read_all, so it is authorized with the
     * permissions of MS03 and it does not return the permissions of each role
     *
     * @param requester identity of whoever queries
     * @return name and description of each role, sorted by name; empty if no role receives alerts
     * @throws ForbiddenOperationException if the requester has neither ms03:alert:manage nor ms03:alert:manage_drill
     */
    List<AlertRecipientRoleResponse> listAlertRecipientRoles(RequesterIdentity requester);

    /**
     * Lists the roles that can be invited to an event (the ones with ms03:alert:receive, because the participants
     * receive the alerts that MS06 asks MS03 for), for the participant picker of the events of MS06. Whoever creates or
     * edits events does not have ms01:role:read_all, so it is authorized with the permissions of MS06 and it does not
     * return the permissions of each role
     *
     * @param requester identity of whoever queries
     * @return name and description of each role, sorted by name; empty if no role receives alerts
     * @throws ForbiddenOperationException if the requester has neither ms06:drill:manage nor ms06:real_event:manage
     */
    List<AlertRecipientRoleResponse> listEventParticipantRoles(RequesterIdentity requester);

        /**
     * Creates a role that is not a system role and without a session cut (form F-10), the attempt is audited in MS08
     *
     * @param requester identity of whoever requests
     * @param request name, description and permissions of the role
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the created role
     * @throws ForbiddenOperationException if the requester does not have the role management permission
     * @throws InvalidRequestException if any data breaks form F-10
     * @throws BusinessRuleException if the permissions include ms01:role:manage
     * @throws ConflictException if a role with that name already exists
     */
    RoleDetailResponse createRole(RequesterIdentity requester, CreateRoleRequest request, String correlationId);

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
    RoleDetailResponse updateRole(RequesterIdentity requester, String role, UpdateRoleRequest request,
            String correlationId);

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
     * @throws InvalidRequestException if any data breaks form F-12
     * @throws RoleNotFoundException if the role does not exist
     * @throws BusinessRuleException if ms01:role:manage stays in a role other than ADMIN or ADMIN loses it
     */
    RoleDetailResponse updatePermissions(RequesterIdentity requester, String role,
            UpdateRolePermissionsRequest request, String correlationId);

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
    void deleteRole(RequesterIdentity requester, String role, String correlationId);
}
