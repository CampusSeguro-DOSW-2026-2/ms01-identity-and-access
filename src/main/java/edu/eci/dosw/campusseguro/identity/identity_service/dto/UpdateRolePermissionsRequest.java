package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de PUT /api/v1/roles/{rol}/permissions (formulario F-12)
 *
 * permisos: lista completa que reemplaza a la actual
 *
 * cualquier campo no definido en el contrato se rechaza con HTTP 400 (RG-04)
 */
public class UpdateRolePermissionsRequest extends RoleRequest {

    @JsonProperty("permisos")
    private List<String> permisos;

    /**
     * Devuelve los permisos del rol
     *
     * @return códigos de permiso recibidos
     */
    public List<String> getPermisos() {
        return permisos;
    }

    /**
     * Coloca los permisos del rol
     *
     * @param permisos códigos de permiso que reemplazan a los actuales
     */
    public void setPermisos(List<String> permisos) {
        this.permisos = permisos;
    }
}
