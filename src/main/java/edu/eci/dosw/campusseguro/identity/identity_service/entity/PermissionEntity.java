package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Permiso del catálogo de CampusSeguro (tabla permisos del modelo de datos), el código es lo que viaja en el
 * token y en las cabeceras, el id es interno y no sale de MS01
 */
@Entity
@Table(name = "permisos")
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String name;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String description;

    /**
     * Devuelve el identificador interno del permiso
     *
     * @return el id del permiso, null si todavía no se guardó
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Devuelve el código del permiso
     *
     * @return el código con el formato modulo:recurso:accion
     */
    public String getName() {
        return this.name;
    }

    /**
     * Coloca el código del permiso
     *
     * @param name código con el formato modulo:recurso:accion
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Devuelve la descripción del permiso
     *
     * @return qué permite hacer el permiso
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Coloca la descripción del permiso
     *
     * @param description qué permite hacer el permiso
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
