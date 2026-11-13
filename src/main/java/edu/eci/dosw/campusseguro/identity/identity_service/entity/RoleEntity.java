package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Rol de CampusSeguro (tabla roles del modelo de datos), el nombre es la clave y viaja en el token y en las
 * cabeceras, los permisos son códigos del catálogo Permission y los administra el administrador
 */
@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    @Column(name = "nombre", nullable = false, length = 30)
    private String name;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "permisos", nullable = false, columnDefinition = "text[]")
    private List<String> permissions = new ArrayList<>();

    @Column(name = "es_sistema", nullable = false, updatable = false)
    private boolean system;

    @Column(name = "valido_desde")
    private Instant validFrom;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;

    /**
     * Devuelve el nombre del rol
     *
     * @return el nombre del rol, en mayúsculas
     */
    public String getName() {
        return this.name;
    }

    /**
     * Coloca el nombre del rol
     *
     * @param name nombre del rol, ya normalizado a mayúsculas
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Devuelve la descripción del rol
     *
     * @return la descripción del rol
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Coloca la descripción del rol
     *
     * @param description descripción del rol
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Devuelve los códigos de permiso del rol
     *
     * @return lista de códigos de permiso, ordenada y sin repetidos
     */
    public List<String> getPermissions() {
        return this.permissions;
    }

    /**
     * Coloca los códigos de permiso del rol
     *
     * @param permissions códigos de permiso, ordenados y sin repetidos
     */
    public void setPermissions(List<String> permissions) {
        this.permissions = new ArrayList<>(permissions);
    }

    /**
     * Indica si el rol es de sistema
     *
     * @return true si el rol no se puede borrar ni renombrar
     */
    public boolean isSystem() {
        return this.system;
    }

    /**
     * Marca el rol como de sistema o no, solo se escribe al crear el rol
     *
     * @param system true si el rol es de sistema
     */
    public void setSystem(boolean system) {
        this.system = system;
    }

    /**
     * Devuelve el corte de sesión del rol
     *
     * @return el instante desde el cual son válidas las credenciales de los usuarios con este rol, null si no
     *         hay corte
     */
    public Instant getValidFrom() {
        return this.validFrom;
    }

    /**
     * Coloca el corte de sesión del rol
     *
     * @param validFrom instante de corte truncado a segundos, null para quitarlo
     */
    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    /**
     * Devuelve la fecha de creación
     *
     * @return el instante en que se creó el rol, null si todavía no se guardó
     */
    public Instant getCreatedAt() {
        return this.createdAt;
    }

    /**
     * Devuelve la fecha de la última modificación
     *
     * @return el instante de la última modificación, null si todavía no se guardó
     */
    public Instant getUpdatedAt() {
        return this.updatedAt;
    }
}
