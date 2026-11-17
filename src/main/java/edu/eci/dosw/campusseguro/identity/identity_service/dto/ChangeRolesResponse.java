package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

/**
 * Response of PATCH /api/v1/users/roles, the final roles of each user after the change
 *
 * @param resultados final roles by user
 */
public record ChangeRolesResponse(List<UserRoles> resultados) {

        /**
     * Final roles of a user
     *
     * @param id id of the user
     * @param roles roles that the user has after the change
     */
    public record UserRoles(String id, List<String> roles) {
    }
}
