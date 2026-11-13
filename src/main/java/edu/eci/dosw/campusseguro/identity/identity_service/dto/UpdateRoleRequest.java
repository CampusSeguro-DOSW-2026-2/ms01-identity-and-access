package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de PUT /api/v1/roles/{rol} (formulario F-11)
 *
 * rol: nombre del rol, igual al actual o uno nuevo, se normaliza antes de validarlo (RG-08)
 * descripcion: texto que muestra la interfaz de administración
 *
 * los permisos no se editan aquí y, como cualquier campo no definido en el contrato, se rechazan con HTTP 400
 * (RG-04)
 */
public class UpdateRoleRequest extends RoleRequest {

    @JsonProperty("rol")
    private String rol;

    @JsonProperty("descripcion")
    private String descripcion;

    /**
     * Devuelve el nombre del rol
     *
     * @return nombre recibido, sin normalizar
     */
    public String getRol() {
        return rol;
    }

    /**
     * Coloca el nombre del rol
     *
     * @param rol nombre del rol
     */
    public void setRol(String rol) {
        this.rol = rol;
    }

    /**
     * Devuelve la descripción del rol
     *
     * @return descripción recibida
     */
    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Coloca la descripción del rol
     *
     * @param descripcion descripción del rol
     */
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
