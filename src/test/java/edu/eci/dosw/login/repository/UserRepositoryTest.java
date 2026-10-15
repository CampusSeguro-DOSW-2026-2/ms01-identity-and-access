package edu.eci.dosw.login.repository;

import edu.eci.dosw.login.entity.UserEntity;
import edu.eci.dosw.login.entity.UserRol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de la capa de persistencia con @DataJpaTest sobre H2 (perfil test),
 * reutilizando el mismo esquema que la base de datos real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Guarda y recupera un usuario por su correo")
    void save_andFindByEmail() {
        userRepository.save(new UserEntity("ana@uni.edu", "$2a$10$hash", UserRol.ESTUDIANTE));

        Optional<UserEntity> found = userRepository.findByEmail("ana@uni.edu");

        assertThat(found).isPresent();
        assertThat(found.get().getRol()).isEqualTo(UserRol.ESTUDIANTE);
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    @DisplayName("Persiste el rol con el valor de la base de datos (con espacios)")
    void save_mapsRolToDatabaseValue() {
        UserEntity saved = userRepository.save(
                new UserEntity("leo@uni.edu", "$2a$10$hash", UserRol.PERSONAL_ADMINISTRATIVO));

        UserEntity reloaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getRol()).isEqualTo(UserRol.PERSONAL_ADMINISTRATIVO);
        assertThat(reloaded.getRol().getDbValue()).isEqualTo("PERSONAL ADMINISTRATIVO");
    }

    @Test
    @DisplayName("existsByEmail devuelve true solo si el correo existe")
    void existsByEmail_reflectsDatabaseState() {
        userRepository.save(new UserEntity("ana@uni.edu", "$2a$10$hash", UserRol.ESTUDIANTE));

        assertThat(userRepository.existsByEmail("ana@uni.edu")).isTrue();
        assertThat(userRepository.existsByEmail("nadie@uni.edu")).isFalse();
    }

    @Test
    @DisplayName("findAll retorna todos los usuarios persistidos")
    void findAll_returnsAllUsers() {
        userRepository.save(new UserEntity("ana@uni.edu", "$2a$10$hash", UserRol.ESTUDIANTE));
        userRepository.save(new UserEntity("leo@uni.edu", "$2a$10$hash", UserRol.PROFESOR));

        assertThat(userRepository.findAll()).hasSize(2);
    }
}