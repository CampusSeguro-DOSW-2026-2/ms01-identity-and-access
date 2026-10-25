package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant updatedAt;

    /**
     * Seccion de gets y sets dentro de la entidad para acceder a su informacion.
     */

    public UUID getId(){ return this.id; }
    public void setId(UUID id){ this.id = id; }

    public String getFirstName(){ return this.firstName; }
    public void setFirstName(String name){ this.firstName = name; }

    public String getSecondName(){ return this.secondName; }
    public void setSecondName(String name){ this.secondName = name; }

    public String getFirstLastName(){ return this.firstLastName; }
    public void setFirstLastName(String lastName){ this.firstLastName = lastName; }

    public String getSecondLastName(){ return this.secondLastName; }
    public void setSecondLastName(String lastName){ this.secondLastName = lastName; }

    public String getEmail(){ return this.email; }
    public void setEmail(String email){ this.email = email; }

    public String getPassword(){ return this.password; }
    public void setPassword(String password){ this.password = password; }

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

    public UserState getState(){ return this.state; }
    public void setState(UserState state){ this.state = state; }

}
