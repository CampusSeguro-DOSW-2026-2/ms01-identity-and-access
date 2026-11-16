package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Rol de CampusSeguro (tabla roles del modelo de datos), el nombre identifica al rol hacia afuera y viaja en el
 * token y en las cabeceras, el id es interno y no sale de MS01, los permisos son filas del catálogo de permisos
 * y los administra el administrador
 */
@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 30)
    private String name;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "rol_permiso", joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<PermissionEntity> permissions = new HashSet<>();

    @Column(name = "es_sistema", nullable = false, updatable = false)
    private boolean system;

    @Column(name = "valido_desde")
    private Instant validFrom;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /**
     * Devuelve el identificador interno del rol
     *
     * @return el id del rol, null si todavía no se guardó
     */
    public UUID getId() {
        return this.id;
    }

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
     * Devuelve los permisos del rol
     *
     * @return conjunto de permisos del catálogo que tiene el rol, nunca es null
     */
    public Set<PermissionEntity> getPermissions() {
        return this.permissions;
    }

    /**
     * Deja al rol con los permisos recibidos sincronizando la colección en sitio, así Hibernate solo borra de
     * rol_permiso los permisos retirados e inserta los nuevos, sin tocar los que se conservan
     *
     * @param permissions permisos finales del rol, null equivale a ninguno
     */
    public void setPermissions(Set<PermissionEntity> permissions) {
        if (permissions == null) {
            this.permissions.clear();
            return;
        }
        if (permissions == this.permissions) {
            return;
        }
        Set<PermissionEntity> finalPermissions = new HashSet<>(permissions);
        this.permissions.retainAll(finalPermissions);
        this.permissions.addAll(finalPermissions);
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
