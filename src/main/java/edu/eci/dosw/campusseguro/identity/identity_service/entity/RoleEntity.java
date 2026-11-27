package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;
import java.util.Collections;
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
 * Role of CampusSeguro (table roles of the data model), the name identifies the role to the outside and travels in the
 * token and in the headers, the id is internal and does not leave MS01, the permissions are rows of the permission
 * catalog and are managed by the administrator
 */
@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<PermissionEntity> permissions = new HashSet<>();

    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    @Column(name = "valid_from")
    private Instant validFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

        /**
     * Returns the internal identifier of the role
     *
     * @return the id of the role, null if it has not been saved yet
     */
    public UUID getId() {
        return this.id;
    }

        /**
     * Returns the name of the role
     *
     * @return the name of the role, in uppercase
     */
    public String getName() {
        return this.name;
    }

        /**
     * Sets the name of the role
     *
     * @param name name of the role, already normalized to uppercase
     */
    public void setName(String name) {
        this.name = name;
    }

        /**
     * Returns the description of the role
     *
     * @return the description of the role
     */
    public String getDescription() {
        return this.description;
    }

        /**
     * Sets the description of the role
     *
     * @param description description of the role
     */
    public void setDescription(String description) {
        this.description = description;
    }

        /**
     * Returns the permissions of the role as a read-only view; they change only through setPermissions
     *
     * @return set of permissions of the catalog that the role has, it is never null
     */
    public Set<PermissionEntity> getPermissions() {
        return Collections.unmodifiableSet(this.permissions);
    }

        /**
     * Leaves the role with the received permissions by synchronizing the collection in place, so Hibernate only deletes
     * from role_permissions the removed permissions and inserts the new ones, without touching the ones that are kept
     *
     * @param permissions final permissions of the role, null is equivalent to none
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
     * Tells whether the role is a system role
     *
     * @return true if the role cannot be deleted or renamed
     */
    public boolean isSystem() {
        return this.system;
    }

        /**
     * Marks the role as a system role or not, it is only written when the role is created
     *
     * @param system true if the role is a system role
     */
    public void setSystem(boolean system) {
        this.system = system;
    }

        /**
     * Returns the session cut of the role
     *
     * @return the instant from which the credentials of the users with this role are valid, null if there is no cut
     */
    public Instant getValidFrom() {
        return this.validFrom;
    }

        /**
     * Sets the session cut of the role
     *
     * @param validFrom cut instant truncated to seconds, null to remove it
     */
    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

        /**
     * Returns the creation date
     *
     * @return the instant at which the role was created, null if it has not been saved yet
     */
    public Instant getCreatedAt() {
        return this.createdAt;
    }

        /**
     * Returns the date of the last modification
     *
     * @return the instant of the last modification, null if it has not been saved yet
     */
    public Instant getUpdatedAt() {
        return this.updatedAt;
    }
}
