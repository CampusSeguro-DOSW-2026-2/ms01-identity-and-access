package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of PATCH /api/v1/users/roles
 *
 * ids: target users
 * agregar: roles added to each user
 * retirar: roles removed from each user
 *
 * any field not defined in the contract is rejected with HTTP 400 (RG-04)
 * the identity of the requester never travels in the body, but in the headers (RG-07)
 */
public class ChangeRolesRequest extends StrictRequest {

    @JsonProperty("ids")
    private List<String> ids;

    @JsonProperty("agregar")
    private List<String> agregar;

    @JsonProperty("retirar")
    private List<String> retirar;

        /**
     * Returns the ids of the users
     *
     * @return ids of the users
     */
    public List<String> getIds() {
        return ids;
    }

        /**
     * Sets the ids of the users
     *
     * @param ids identifiers of the users whose roles are changed
     */
    public void setIds(List<String> ids) {
        this.ids = ids;
    }

        /**
     * Returns the roles that must be added
     *
     * @return role names received
     */
    public List<String> getAgregar() {
        return agregar;
    }

        /**
     * Sets the roles that must be added
     *
     * @param agregar names of the roles added to each user
     */
    public void setAgregar(List<String> agregar) {
        this.agregar = agregar;
    }

        /**
     * Returns the roles that must be removed
     *
     * @return role names received
     */
    public List<String> getRetirar() {
        return retirar;
    }

        /**
     * Sets the roles that must be removed
     *
     * @param retirar names of the roles removed from each user
     */
    public void setRetirar(List<String> retirar) {
        this.retirar = retirar;
    }
}
