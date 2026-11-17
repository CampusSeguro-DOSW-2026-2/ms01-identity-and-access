package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of PUT /api/v1/roles/{rol} (form F-11)
 *
 * rol: name of the role, the same as the current one or a new one, normalized before validating it (RG-08)
 * descripcion: text shown by the administration interface
 *
 * the permissions are not edited here and, like any field not defined in the contract, they are rejected with HTTP 400
 * (RG-04)
 */
public class UpdateRoleRequest extends RoleRequest {

    @JsonProperty("rol")
    private String rol;

    @JsonProperty("descripcion")
    private String descripcion;

        /**
     * Returns the name of the role
     *
     * @return name received, not normalized
     */
    public String getRol() {
        return rol;
    }

        /**
     * Sets the name of the role
     *
     * @param rol name of the role
     */
    public void setRol(String rol) {
        this.rol = rol;
    }

        /**
     * Returns the description of the role
     *
     * @return description received
     */
    public String getDescripcion() {
        return descripcion;
    }

        /**
     * Sets the description of the role
     *
     * @param descripcion description of the role
     */
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
