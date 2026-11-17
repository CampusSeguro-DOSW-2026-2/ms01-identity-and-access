package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of PUT /api/v1/roles/{rol}/permissions (form F-12)
 *
 * permisos: complete list that replaces the current one
 *
 * any field not defined in the contract is rejected with HTTP 400 (RG-04)
 */
public class UpdateRolePermissionsRequest extends RoleRequest {

    @JsonProperty("permisos")
    private List<String> permisos;

        /**
     * Returns the permissions of the role
     *
     * @return permission codes received
     */
    public List<String> getPermisos() {
        return permisos;
    }

        /**
     * Sets the permissions of the role
     *
     * @param permisos permission codes that replace the current ones
     */
    public void setPermisos(List<String> permisos) {
        this.permisos = permisos;
    }
}
