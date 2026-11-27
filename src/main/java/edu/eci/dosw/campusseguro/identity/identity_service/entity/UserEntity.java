package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * User registered in MS01 (table users of the data model), the email is always stored in lowercase and the password
 * only as a hash
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 30)
    private String firstName;

    @Column(name = "second_name", length = 30)
    private String secondName;

    @Column(name = "first_lastname", nullable = false, length = 30)
    private String firstLastName;

    @Column(name = "second_lastname", length = 30)
    private String secondLastName;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserState state;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "valid_from")
    private Instant validFrom;

        /**
     * Returns the identifier of the user
     *
     * @return the id of the user, null if it has not been saved yet
     */
    public UUID getId() {
        return this.id;
    }

        /**
     * Sets the identifier of the user
     *
     * @param id id of the user
     */
    public void setId(UUID id) {
        this.id = id;
    }

        /**
     * Returns the first name
     *
     * @return the first name of the user
     */
    public String getFirstName() {
        return this.firstName;
    }

        /**
     * Sets the first name
     *
     * @param name first name of the user
     */
    public void setFirstName(String name) {
        this.firstName = name;
    }

        /**
     * Returns the second name
     *
     * @return the second name, null if there is none
     */
    public String getSecondName() {
        return this.secondName;
    }

        /**
     * Sets the second name
     *
     * @param name second name of the user, null if there is none
     */
    public void setSecondName(String name) {
        this.secondName = name;
    }

        /**
     * Returns the first lastname
     *
     * @return the first lastname of the user
     */
    public String getFirstLastName() {
        return this.firstLastName;
    }

        /**
     * Sets the first lastname
     *
     * @param lastName first lastname of the user
     */
    public void setFirstLastName(String lastName) {
        this.firstLastName = lastName;
    }

        /**
     * Returns the second lastname
     *
     * @return the second lastname, null if there is none
     */
    public String getSecondLastName() {
        return this.secondLastName;
    }

        /**
     * Sets the second lastname
     *
     * @param lastName second lastname of the user, null if there is none
     */
    public void setSecondLastName(String lastName) {
        this.secondLastName = lastName;
    }

        /**
     * Returns the institutional email
     *
     * @return the email of the user, in lowercase
     */
    public String getEmail() {
        return this.email;
    }

        /**
     * Sets the institutional email
     *
     * @param email email of the user, already normalized to lowercase
     */
    public void setEmail(String email) {
        this.email = email;
    }

        /**
     * Returns the hash of the password
     *
     * @return the BCrypt hash of the password, never the plain text
     */
    public String getPassword() {
        return this.password;
    }

        /**
     * Sets the hash of the password
     *
     * @param password BCrypt hash of the password
     */
    public void setPassword(String password) {
        this.password = password;
    }

        /**
     * Returns the roles of the user as a read-only view, it is never null and has no defined order; they change only
     * through setRoles
     *
     * @return set of roles of the user
     */
    public Set<RoleEntity> getRoles() {
        return Collections.unmodifiableSet(this.roles);
    }

        /**
     * Returns the names of the roles of the user as they appear in the API and in the token
     *
     * @return the role names in alphabetical order
     */
    public List<String> getRoleNames() {
        return this.roles.stream()
                .map(RoleEntity::getName)
                .sorted()
                .toList();
    }

        /**
     * Leaves the user with the received roles by synchronizing the collection in place, so Hibernate only inserts into
     * user_roles the new roles and deletes the removed ones, without touching the ones that are kept or their
     * assignment date (4_modelo-datos, batch role change). Assigning a new collection to an already loaded user would
     * make it delete and insert again all of its rows
     *
     * @param roles final roles of the user, null is equivalent to none
     */
    public void setRoles(Set<RoleEntity> roles) {
        if (roles == null) {
            this.roles.clear();
            return;
        }
        if (roles == this.roles) {
            return;
        }
        Set<RoleEntity> finalRoles = new HashSet<>(roles);
        this.roles.retainAll(finalRoles);
        this.roles.addAll(finalRoles);
    }

        /**
     * Returns the status of the account
     *
     * @return the status of the user
     */
    public UserState getState() {
        return this.state;
    }

        /**
     * Sets the status of the account
     *
     * @param state status of the user
     */
    public void setState(UserState state) {
        this.state = state;
    }

        /**
     * Returns the registration date
     *
     * @return the instant at which the user was created, null if it has not been saved yet
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

        /**
     * Returns the session cut of the user
     *
     * @return the instant from which the credentials of the user are valid, null if there is no cut
     */
    public Instant getValidFrom() {
        return this.validFrom;
    }

        /**
     * Sets the session cut of the user, the credentials issued before that instant are rejected
     *
     * @param validFrom cut instant truncated to seconds, null to remove it
     */
    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }
}
