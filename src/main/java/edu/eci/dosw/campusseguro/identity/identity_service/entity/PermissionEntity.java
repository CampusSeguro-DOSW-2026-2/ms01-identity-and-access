package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Permission of the CampusSeguro catalog (table permissions of the data model), the code is what travels in the token
 * and in the headers, the id is internal and does not leave MS01
 */
@Entity
@Table(name = "permissions")
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

        /**
     * Returns the internal identifier of the permission
     *
     * @return the id of the permission, null if it has not been saved yet
     */
    public UUID getId() {
        return this.id;
    }

        /**
     * Returns the code of the permission
     *
     * @return the code with the format module:resource:action
     */
    public String getName() {
        return this.name;
    }

        /**
     * Sets the code of the permission
     *
     * @param name code with the format module:resource:action
     */
    public void setName(String name) {
        this.name = name;
    }

        /**
     * Returns the description of the permission
     *
     * @return what the permission allows to do
     */
    public String getDescription() {
        return this.description;
    }

        /**
     * Sets the description of the permission
     *
     * @param description what the permission allows to do
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
