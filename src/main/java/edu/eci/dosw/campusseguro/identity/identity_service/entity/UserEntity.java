package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Usuario registrado en MS01 (tabla usuarios del modelo de datos), el correo se guarda siempre en
 * minúsculas y la contraseña solo como hash
 */
@Entity
@Table(name = "usuarios")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "primer_nombre", nullable = false, length = 30)
    private String firstName;

    @Column(name = "segundo_nombre", length = 30)
    private String secondName;

    @Column(name = "primer_apellido", nullable = false, length = 30)
    private String firstLastName;

    @Column(name = "segundo_apellido", length = 30)
    private String secondLastName;

    @Column(name = "correo", nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String password;

    @ElementCollection(targetClass = UserRole.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Set<UserRole> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private UserState state;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;

    /**
     * Devuelve el identificador del usuario
     *
     * @return el id del usuario, null si todavía no se guardó
     */
    public UUID getId() {
        return this.id;
    }

    /**
     * Coloca el identificador del usuario
     *
     * @param id id del usuario
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Devuelve el primer nombre
     *
     * @return el primer nombre del usuario
     */
    public String getFirstName() {
        return this.firstName;
    }

    /**
     * Coloca el primer nombre
     *
     * @param name primer nombre del usuario
     */
    public void setFirstName(String name) {
        this.firstName = name;
    }

    /**
     * Devuelve el segundo nombre
     *
     * @return el segundo nombre, null si no tiene
     */
    public String getSecondName() {
        return this.secondName;
    }

    /**
     * Coloca el segundo nombre
     *
     * @param name segundo nombre del usuario, null si no tiene
     */
    public void setSecondName(String name) {
        this.secondName = name;
    }

    /**
     * Devuelve el primer apellido
     *
     * @return el primer apellido del usuario
     */
    public String getFirstLastName() {
        return this.firstLastName;
    }

    /**
     * Coloca el primer apellido
     *
     * @param lastName primer apellido del usuario
     */
    public void setFirstLastName(String lastName) {
        this.firstLastName = lastName;
    }

    /**
     * Devuelve el segundo apellido
     *
     * @return el segundo apellido, null si no tiene
     */
    public String getSecondLastName() {
        return this.secondLastName;
    }

    /**
     * Coloca el segundo apellido
     *
     * @param lastName segundo apellido del usuario, null si no tiene
     */
    public void setSecondLastName(String lastName) {
        this.secondLastName = lastName;
    }

    /**
     * Devuelve el correo institucional
     *
     * @return el correo del usuario, en minúsculas
     */
    public String getEmail() {
        return this.email;
    }

    /**
     * Coloca el correo institucional
     *
     * @param email correo del usuario, ya normalizado a minúsculas
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Devuelve el hash de la contraseña
     *
     * @return el hash BCrypt de la contraseña, nunca el texto plano
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * Coloca el hash de la contraseña
     *
     * @param password hash BCrypt de la contraseña
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Devuelve los roles del usuario, nunca es null y no tiene un orden definido, para entregarlos
     * ordenados se usa UserRole.sortedByName
     *
     * @return conjunto de roles del usuario
     */
    public Set<UserRole> getRoles() {
        return this.roles;
    }

    /**
     * Deja al usuario con los roles recibidos sincronizando la coleccion en sitio, asi Hibernate solo
     * inserta en usuario_rol los roles nuevos y borra los retirados, sin tocar los que se conservan ni su
     * fecha de asignacion (5_modelo_datos, cambio de roles en lote). Asignar una coleccion nueva a un
     * usuario ya cargado haria que borre e inserte de nuevo todas sus filas
     *
     * @param roles roles finales del usuario, null equivale a ninguno
     */
    public void setRoles(Set<UserRole> roles) {
        if (roles == null) {
            this.roles.clear();
            return;
        }
        if (roles == this.roles) {
            return;
        }
        Set<UserRole> finalRoles = new HashSet<>(roles);
        this.roles.retainAll(finalRoles);
        this.roles.addAll(finalRoles);
    }

    /**
     * Devuelve el estado de la cuenta
     *
     * @return el estado del usuario
     */
    public UserState getState() {
        return this.state;
    }

    /**
     * Coloca el estado de la cuenta
     *
     * @param state estado del usuario
     */
    public void setState(UserState state) {
        this.state = state;
    }

    /**
     * Devuelve la fecha de registro
     *
     * @return el instante en que se creó el usuario, null si todavía no se guardó
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
