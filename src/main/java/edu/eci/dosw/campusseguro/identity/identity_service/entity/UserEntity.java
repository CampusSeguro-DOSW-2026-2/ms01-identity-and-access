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

    @Column(nullable = false, length = 30)
    private String primer_nombre;

    @Column(length = 30)
    private String segundo_nombre;

    @Column(nullable = false, length = 30)
    private String primer_apellido;

    @Column(length = 30)
    private String segundo_apellido;

    @Column(nullable = false, length = 100)
    private String correo;

    @Column(nullable = false, length = 30)
    private String password_hash;

    @ElementCollection(targetClass = UserRole.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private List<UserRole> rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserState estado;

    /**
     * Seccion de gets y sets dentro de la entidad para acceder a su informacion.
     */

    public UUID getId(){ return this.id; }
    public void setId(UUID id){ this.id = id; }

    public String getFirstName(){ return this.primer_nombre; }
    public void setFirstName(String name){ this.primer_nombre = name; }

    public String getSecondName(){ return this.segundo_nombre; }
    public void setSecondtName(String name){ this.segundo_nombre = name; }

    public String getFirstLastName(){ return this.primer_apellido; }
    public void setFirstLastName(String lastName){ this.primer_apellido = lastName; }

    public String getSecondLastName(){ return this.segundo_apellido; }
    public void setSecondLastName(String lastName){ this.segundo_apellido = lastName; }

    public String getEmail(){ return this.correo; }
    public void setEmail(String email){ this.correo = email; }

    public String getPassword(){ return this.password_hash; }
    public void setPassword(String password){ this.password_hash = password; }

    public List<UserRole> getRole(){ return this.rol; }
    public void setRole(List<UserRole> role){ this.rol = role; }

    public UserState getState(){ return this.estado; }
    public void setState(UserState state){ this.estado = state; }

}
