package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Query and administration of users (RF-MS01-07) and management of the own profile (RF-MS01-06), the identity of the
 * requester always arrives in the headers injected by the API Gateway
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

        /**
     * Creates the controller with its service
     *
     * @param userService user service
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

        /**
     * GET /api/v1/users: lists and searches users with pagination (RF-MS01-07, form F-07)
     *
     * 200 page of users, 400 invalid parameters, 401 missing identity, 403 no permission
     *
     * @param requester identity of the requester taken from the headers
     * @param correo partial search by email
     * @param estado filter by status
     * @param rol filter by role
     * @param page page number, it is received as text to validate it with the message of the form
     * @param size page size, it is received as text to validate it with the message of the form
     * @return the page of users
     */
    @GetMapping
    public ResponseEntity<PageResponse<UserProfileResponse>> searchUsers(
            RequesterIdentity requester,
            @RequestParam(required = false) String correo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return ResponseEntity.ok(userService.search(requester, correo, estado, rol, page, size));
    }

        /**
     * PATCH /api/v1/users/status: activa o inactiva a uno o varios usuarios (RF-MS01-07, formulario F-08)
     *
     * 200 previous and new status per user, 400 invalid data, 401 missing identity, 403 no permission or attempt to
     * change their own status, 404 some user does not exist
     *
     * @param requester identity of the requester taken from the headers
     * @param request target users and new status
     * @param httpRequest request in progress, from which the correlation id is obtained
     * @return the previous and the new status of each user
     */
    @PatchMapping("/status")
    public ResponseEntity<ChangeStatusResponse> changeUserStatus(
            RequesterIdentity requester,
            @RequestBody ChangeStatusRequest request,
            HttpServletRequest httpRequest) {
        ChangeStatusResponse response = userService.changeUserStatus(requester, request,
                CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(response);
    }

        /**
     * GET /api/v1/users/me: queries the own profile (RF-MS01-06)
     *
     * 200 profile of the requester, 401 missing identity, 403 no permission, 404 non-existent user
     *
     * @param requester identity of the requester taken from the headers
     * @return the profile of the requester
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(RequesterIdentity requester) {
        return ResponseEntity.ok(userService.getMyProfile(requester));
    }

        /**
     * PUT /api/v1/users/me: updates the own profile (RF-MS01-06, form F-03)
     *
     * 200 profile updated, 400 invalid data or non-editable fields, 401 missing identity, 403 no permission, 404
     * non-existent user
     *
     * @param requester identity of the requester taken from the headers
     * @param request new names and lastnames
     * @return the updated profile
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            RequesterIdentity requester,
            @RequestBody UserUpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(requester, request));
    }

        /**
     * GET /api/v1/users/{id}: queries a user by their id (RF-MS01-07)
     *
     * 200 user, 400 id with an invalid format, 401 missing identity, 403 no permission, 404 non-existent user
     *
     * @param requester identity of the requester taken from the headers
     * @param id id of the queried user
     * @return the data of the user
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getUserById(RequesterIdentity requester, @PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(requester, id));
    }

        /**
     * POST /api/v1/users/batch: internal query of several users by id (RF-MS01-09), only for the authorized services,
     * which identify themselves with X-Service-Name
     *
     * 200 users found and ids not found, 400 invalid list, 401 unidentified service, 403 unauthorized service
     *
     * @param request ids of the users
     * @return the users found and the ids that do not exist
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchUsersResponse> findUsersByIds(@RequestBody BatchUsersRequest request) {
        return ResponseEntity.ok(userService.findUsersByIds(request));
    }

        /**
     * GET /api/v1/users/by-role: internal query of the users that have a role (RF-MS01-09), only for the authorized
     * services, the literal route takes priority over /{id}
     *
     * 200 page of users, 400 invalid parameters, 401 unidentified service, 403 unauthorized service
     *
     * @param rol required role
     * @param creadoAntes cut-off instant in ISO 8601, optional
     * @param page page number, it is received as text to validate it with the message of the contract
     * @param size page size, it is received as text to validate it with the message of the contract
     * @return the page of users with that role
     */
    @GetMapping("/by-role")
    public ResponseEntity<PageResponse<UserProfileResponse>> searchByRole(
            @RequestParam(required = false) String rol,
            @RequestParam(name = "creado_antes", required = false) String creadoAntes,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return ResponseEntity.ok(userService.searchByRole(rol, creadoAntes, page, size));
    }
}
