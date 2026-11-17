package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Query of roles with their permissions and role change of the users (RF-MS01-05)
 */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService roleService;

        /**
     * Creates the controller with its service
     *
     * @param roleService role and permission management service
     */
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

        /**
     * GET /api/v1/roles: roles with their permissions (RF-MS01-05)
     *
     * 200 array of roles with their permissions, 401 missing or invalid identity
     *
     * @param requester identity of the requester taken from the headers
     * @return the list of roles visible to the requester and the codes of their permissions
     */
    @GetMapping("/roles")
    public ResponseEntity<List<RolePermissionsResponse>> getRoles(RequesterIdentity requester) {
        return ResponseEntity.ok(roleService.getRoles(requester));
    }

        /**
     * PATCH /api/v1/users/roles: adds and removes roles to one or several users (RF-MS01-05, form F-09)
     *
     * 200 resulting roles per user, 400 invalid data or a user that would be left without roles, 401 missing identity,
     * 403 no permission or attempt to modify their own roles, 404 some user does not exist
     *
     * @param requester identity of the requester taken from the headers
     * @param request target users and roles to add and to remove
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the final roles of each user
     */
    @PatchMapping("/users/roles")
    public ResponseEntity<ChangeRolesResponse> changeRoles(
            RequesterIdentity requester,
            @RequestBody ChangeRolesRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(roleService.changeRoles(requester, request, CorrelationIds.current(httpRequest)));
    }
}
