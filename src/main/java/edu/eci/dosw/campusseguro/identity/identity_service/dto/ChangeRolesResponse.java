package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * Respuesta de PATCH /api/v1/users/roles, los roles finales de cada usuario despues del cambio
 *
 * @param resultados roles finales por usuario
 */
public record ChangeRolesResponse(List<UserRoles> resultados) {

    /**
     * Roles finales de un usuario
     *
     * @param id id del usuario
     * @param roles roles que tiene el usuario despues del cambio
     */
    public record UserRoles(String id, List<UserRole> roles) {
    }
}
