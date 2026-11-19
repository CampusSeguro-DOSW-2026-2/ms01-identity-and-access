package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.AlertRecipientRoleResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RoleDetailResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleAdminService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Administration of roles and of the permission catalog (RF-MS01-10)
 */
@RestController
@RequestMapping("/api/v1")
public class RoleAdminController {

    public static final String ROLES_PATH = "/api/v1/roles/";

    private final RoleAdminService roleAdminService;

        /**
     * Creates the controller with its service
     *
     * @param roleAdminService role and permission administration service
     */
    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

        /**
     * GET /api/v1/roles/details: lists the roles with their detail (RF-MS01-10)
     *
     * 200 roles sorted by name, 401 missing or invalid identity, 403 no permission
     *
     * @param requester identity of the requester taken from the headers
     * @return the roles with their description, their permissions and the system flag
     */
    @GetMapping("/roles/details")
    public ResponseEntity<List<RoleDetailResponse>> listRoles(RequesterIdentity requester) {
        return ResponseEntity.ok(roleAdminService.listRoles(requester));
    }

        /**
     * GET /api/v1/roles/alert-recipients: roles that can receive alerts, for the recipient picker of MS03 (RF-MS01-10),
     * the literal route takes priority over /roles/{rol} and no role can be named like it (the hyphen is not allowed)
     *
     * 200 roles with ms03:alert:receive sorted by name, 401 missing identity, 403 without ms03:alert:manage or
     * ms03:alert:manage_drill
     *
     * @param requester identity of the requester taken from the headers
     * @return name and description of each role
     */
    @GetMapping("/roles/alert-recipients")
    public ResponseEntity<List<AlertRecipientRoleResponse>> listAlertRecipientRoles(RequesterIdentity requester) {
        return ResponseEntity.ok(roleAdminService.listAlertRecipientRoles(requester));
    }

        /**
     * GET /api/v1/roles/event-participants: roles that can be invited to an event, for the participant picker of MS06
     * (RF-EVE-01), the literal route takes priority over /roles/{rol} and no role can be named like it (the hyphen is
     * not allowed)
     *
     * 200 roles with ms03:alert:receive sorted by name, 401 missing identity, 403 without ms06:drill:manage or
     * ms06:real_event:manage
     *
     * @param requester identity of the requester taken from the headers
     * @return name and description of each role
     */
    @GetMapping("/roles/event-participants")
    public ResponseEntity<List<AlertRecipientRoleResponse>> listEventParticipantRoles(RequesterIdentity requester) {
        return ResponseEntity.ok(roleAdminService.listEventParticipantRoles(requester));
    }

        /**
     * GET /api/v1/roles/{rol}: queries a role (RF-MS01-10)
     *
     * 200 the role, 400 name with an invalid format, 401 missing identity, 403 no permission, 404 non-existent role
     *
     * @param requester identity of the requester taken from the headers
     * @param rol name of the role, it is normalized
     * @return the detail of the role
     */
    @GetMapping("/roles/{rol}")
    public ResponseEntity<RoleDetailResponse> getRole(RequesterIdentity requester, @PathVariable("rol") String rol) {
        return ResponseEntity.ok(roleAdminService.getRole(requester, rol));
    }

        /**
     * GET /api/v1/permissions: permission catalog (RF-MS01-10)
     *
     * 200 permissions sorted by code, 401 missing identity, 403 no permission
     *
     * @param requester identity of the requester taken from the headers
     * @return each permission with its description
     */
    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionItemResponse>> listPermissions(RequesterIdentity requester) {
        return ResponseEntity.ok(roleAdminService.listPermissions(requester));
    }

        /**
     * POST /api/v1/roles: creates a role (RF-MS01-10, form F-10)
     *
     * 201 role created with its path in Location, 400 invalid data or field not allowed, 401 missing identity, 403 no
     * permission, 409 the name already exists, 422 includes ms01:role:manage
     *
     * @param requester identity of the requester taken from the headers
     * @param request name, description and permissions of the role
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the created role
     */
    @PostMapping("/roles")
    public ResponseEntity<RoleDetailResponse> createRole(
            RequesterIdentity requester,
            @RequestBody CreateRoleRequest request,
            HttpServletRequest httpRequest) {
        RoleDetailResponse created = roleAdminService.createRole(requester, request,
                CorrelationIds.current(httpRequest));
        return ResponseEntity.created(URI.create(ROLES_PATH + created.rol())).body(created);
    }

        /**
     * PUT /api/v1/roles/{rol}: changes the name and the description of a role (RF-MS01-10, form F-11)
     *
     * 200 role updated, 400 invalid data or field not allowed, 401 missing identity, 403 no permission, 404
     * non-existent role, 409 repeated name or system role renamed
     *
     * @param requester identity of the requester taken from the headers
     * @param rol current name of the role, it is normalized
     * @param request new name and description
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the updated role
     */
    @PutMapping("/roles/{rol}")
    public ResponseEntity<RoleDetailResponse> updateRole(
            RequesterIdentity requester,
            @PathVariable("rol") String rol,
            @RequestBody UpdateRoleRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(roleAdminService.updateRole(requester, rol, request,
                CorrelationIds.current(httpRequest)));
    }

        /**
     * PUT /api/v1/roles/{rol}/permissions: replaces the permissions of a role (RF-MS01-10, form F-12)
     *
     * 200 role updated, 400 invalid list or field not allowed, 401 missing identity, 403 no permission, 404
     * non-existent role, 422 breaks a rule about the mandatory permissions or about ms01:role:manage
     *
     * @param requester identity of the requester taken from the headers
     * @param rol name of the role, it is normalized
     * @param request complete list of permissions
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the updated role
     */
    @PutMapping("/roles/{rol}/permissions")
    public ResponseEntity<RoleDetailResponse> updatePermissions(
            RequesterIdentity requester,
            @PathVariable("rol") String rol,
            @RequestBody UpdateRolePermissionsRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(roleAdminService.updatePermissions(requester, rol, request,
                CorrelationIds.current(httpRequest)));
    }

        /**
     * DELETE /api/v1/roles/{rol}: deletes a role (RF-MS01-10, form F-13)
     *
     * 204 role deleted without a body, 400 name with an invalid format, 401 missing identity, 403 no permission, 404
     * non-existent role, 409 system role
     *
     * @param requester identity of the requester taken from the headers
     * @param rol name of the role, it is normalized
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return empty response
     */
    @DeleteMapping("/roles/{rol}")
    public ResponseEntity<Void> deleteRole(
            RequesterIdentity requester,
            @PathVariable("rol") String rol,
            HttpServletRequest httpRequest) {
        roleAdminService.deleteRole(requester, rol, CorrelationIds.current(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
