package edu.eci.dosw.login.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Entidad que representa la tabla users.
 *
 * <pre>
 * CREATE TABLE users(
 *     id    BIGSERIAL PRIMARY KEY,
 *     email VARCHAR(120) NOT NULL,
 *     pw    VARCHAR(10)  NOT NULL,
 *     rol   VARCHAR(100) NOT NULL CHECK (...)
 * );
 * </pre>
 *
 * Nota: la longitud de {@code pw} se amplia a 255 mediante
 * database/002_migrate_pw_length.sql para poder almacenar el hash BCrypt.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, length = 120, unique = true)
    private String email;

    /** Hash BCrypt del password. Nunca se expone en respuestas HTTP. */
    @Column(name = "pw", nullable = false, length = 255)
    private String password;

    @Convert(converter = UserRolConverter.class)
    @Column(name = "rol", nullable = false, length = 100)
    private UserRol rol;

    public UserEntity() {
    }

    public UserEntity(String email, String password, UserRol rol) {
        this.email = email;
        this.password = password;
        this.rol = rol;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserRol getRol() {
        return this.rol;
    }

    public void setRol(UserRol rol) {
        this.rol = rol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserEntity that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}