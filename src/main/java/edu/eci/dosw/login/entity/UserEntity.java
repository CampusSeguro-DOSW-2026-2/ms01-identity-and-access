package edu.eci.dosw.login.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 10)
    private String password;

    @Column(nullable = false, length = 100)
    private UserRol rol;

    /**
     * Gets y Sets asociados a la tabla para poder acceder a la informacion de los usuarios
     * en la base de datos
     */

    public Long getId(){ return this.id; }
    public void setId(Long id){ this.id = id; }

    public String getEmail(){ return this.email; }
    public void setEmail(String email){ this.email = email; }

    public String getPassword(){ return this.password; }
    public void setPassword(String password){ this.password = password; }

    public UserRol getRol(){ return this.rol; }
    public void setRol(UserRol rol){ this.rol = rol; }
}
