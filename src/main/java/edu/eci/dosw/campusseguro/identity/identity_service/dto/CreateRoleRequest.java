package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /api/v1/roles (form F-10)
 *
 * rol: name of the role, normalized before validating it (RG-08)
 * descripcion: text shown by the administration interface
 * permisos: permission codes of the role
 *
 * any field not defined in the contract is rejected with HTTP 400 (RG-04)
 */
public class CreateRoleRequest extends RoleRequest {

    @JsonProperty("rol")
    private String rol;

    @JsonProperty("descripcion")
    private String descripcion;

    @JsonProperty("permisos")
    private List<String> permisos;

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
     * @param rol name of the role being created
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
     * @param descripcion description of the role being created
     */
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

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
     * @param permisos permission codes of the role being created
     */
    public void setPermisos(List<String> permisos) {
        this.permisos = permisos;
    }
}
