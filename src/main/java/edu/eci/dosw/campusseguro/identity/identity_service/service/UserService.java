package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Management of the own profile (RF-MS01-06) and user administration: query and activation or deactivation
 * (RF-MS01-07), the registration is in RegistrationService (RF-MS01-01)
 */
public interface UserService {

        /**
     * Queries the profile of the requester themselves (RF-MS01-03, RF-MS01-06)
     *
     * @param requester identity of whoever queries
     * @return the data of the user identified by the identity header
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws UserNotFoundException if the user no longer exists
     */
    UserProfileResponse getMyProfile(RequesterIdentity requester);

        /**
     * Updates the own profile (RF-MS01-06), the update replaces the complete profile
     *
     * @param requester identity of whoever updates
     * @param request new names and lastnames
     * @return the updated profile
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws InvalidRequestException if the data breaks form F-03
     * @throws UserNotFoundException if the user no longer exists
     */
    UserProfileResponse updateProfile(RequesterIdentity requester, UserUpdateProfileRequest request);

        /**
     * Queries a user by their id (RF-MS01-05, RF-MS01-07)
     *
     * @param requester identity of whoever queries
     * @param id id of the queried user
     * @return the data of the user
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws UserNotFoundException if the user does not exist
     */
    UserProfileResponse getUserById(RequesterIdentity requester, UUID id);

        /**
     * Lists and searches users with pagination and optional filters (RF-MS01-07, form F-07)
     *
     * @param requester identity of whoever queries
     * @param correo partial search by email, may be null
     * @param estado status filter, may be null
     * @param rol role filter, may be null
     * @param page page number received as text, null is equivalent to 0
     * @param size page size received as text, null is equivalent to 20
     * @return the page of users with their complete roles
     * @throws ForbiddenOperationException if the requester does not have the required permission
     * @throws InvalidRequestException if any parameter breaks form F-07 or the role does not exist
     */
    PageResponse<UserProfileResponse> search(RequesterIdentity requester, String correo, String estado, String rol,
            String page, String size);

        /**
     * Activates or deactivates one or several users in a single atomic operation (RF-MS01-07, form F-08), every
     * processed user is audited in MS08
     *
     * @param requester identity of whoever requests
     * @param request target users and new status
     * @param correlationId value of X-Correlation-ID, if it is missing one is generated for logs and audit
     * @return the previous and the new status of each user
     * @throws ForbiddenOperationException if the requester does not have the required permission or includes themselves
     * @throws InvalidRequestException if the body breaks form F-08
     * @throws UsersNotFoundException if any target user does not exist
     */
    ChangeStatusResponse changeUserStatus(RequesterIdentity requester, ChangeStatusRequest request,
            String correlationId);

        /**
     * Internal query of several users by id (RF-MS01-09), it is authorized by the internal security chain through the
     * calling service and is not audited
     *
     * @param request ids of the users, from 1 to 100
     * @return the users found in the requested order, without repeated ones, and the ids that do not exist
     * @throws InvalidRequestException if the list is missing, has more than 100 ids or any id is not a UUID
     */
    BatchUsersResponse findUsersByIds(BatchUsersRequest request);

        /**
     * Internal query of the users that have a role (RF-MS01-09), sorted by id, it is authorized by the internal
     * security chain through the calling service and is not audited
     *
     * @param rol required role
     * @param creadoAntes cut-off instant in ISO 8601, may be null
     * @param page page number received as text, null is equivalent to 0
     * @param size page size received as text, null is equivalent to 20
     * @return the page of users with that role, active and inactive
     * @throws InvalidRequestException if any parameter is not valid or the role does not exist
     */
    PageResponse<UserProfileResponse> searchByRole(String rol, String creadoAntes, String page, String size);
}
