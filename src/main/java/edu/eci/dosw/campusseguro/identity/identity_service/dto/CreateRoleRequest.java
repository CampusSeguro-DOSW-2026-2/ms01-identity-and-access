package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de POST /api/v1/roles (formulario F-10)
 *
 * rol: nombre del rol, se normaliza antes de validarlo (RG-08)
 * descripcion: texto que muestra la interfaz de administración
 * permisos: códigos de permiso del rol
 *
 * cualquier campo no definido en el contrato se rechaza con HTTP 400 (RG-04)
 */
public class CreateRoleRequest extends RoleRequest {

    @JsonProperty("rol")
    private String rol;

    @JsonProperty("descripcion")
    private String descripcion;

    @JsonProperty("permisos")
    private List<String> permisos;

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
     * @param rol nombre del rol que se crea
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
     * @param descripcion descripción del rol que se crea
     */
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

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
     * @param permisos códigos de permiso del rol que se crea
     */
    public void setPermisos(List<String> permisos) {
        this.permisos = permisos;
    }
}
