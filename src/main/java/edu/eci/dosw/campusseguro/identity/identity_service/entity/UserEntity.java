package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.util.List;
import java.util.UUID;

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

    @Column(name = "password_hash", nullable = false, length = 30)
    private String password;

    @ElementCollection(targetClass = UserRole.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private List<UserRole> roles;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private UserState state;

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

    public List<UserRole> getRoles(){ return this.roles; }
    public void setRoles(List<UserRole> role){ this.roles = role; }

    public UserState getState(){ return this.state; }
    public void setState(UserState state){ this.state = state; }

}
