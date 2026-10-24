package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de PATCH /api/v1/users/roles
 * 
 * ids: usuarios objetivo
 * agregar: roles que se agregan a cada usuario
 * retirar: roles que se retiran a cada usuario
 * 
 * cualquier campo no definido en el contrato se rechaza con HTTP 400
 * la identidad de quien solicita nunca viaja en el cuerpo, sino en las cabeceras
 */
public class ChangeRolesRequest {

    @JsonProperty("ids")
    private List<String> ids;

    @JsonProperty("agregar")
    private List<String> agregar;

    @JsonProperty("retirar")
    private List<String> retirar;

    /**
     * Devuelve los ids de los usuarios
     *
     * @return ids de los usuarios
     */
    public List<String> getIds() {
        return ids;
    }

    /**
     * Coloca los ids de los usuarios
     *
     * @param ids identificadores de los usuarios a los que se les cambian los roles
     */
    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    /**
     * Devuelve los roles que se deben agregar
     *
     * @return nombres de roles recibidos
     */
    public List<String> getAgregar() {
        return agregar;
    }

    /**
     * Coloca los roles que se deben agregar
     *
     * @param agregar nombres de los roles que se agregan a cada usuario
     */
    public void setAgregar(List<String> agregar) {
        this.agregar = agregar;
    }

    /**
     * Devuelve los roles que se deben retirar
     *
     * @return nombres de roles recibidos
     */
    public List<String> getRetirar() {
        return retirar;
    }

    /**
     * Coloca los roles que se deben retirar
     *
     * @param retirar nombres de los roles que se retiran a cada usuario
     */
    public void setRetirar(List<String> retirar) {
        this.retirar = retirar;
    }

    /**
     * RG-04: Cualquier propiedad desconocida hace fallar la deserializacion
     *
     * @param name nombre de la propiedad no definida en el contrato
     * @param value valor recibido para esa propiedad
     */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Campo no permitido: " + name);
    }
}
