package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import jakarta.persistence.LockModeType;

/**
 * Acceso a los usuarios y a sus roles (tablas usuarios y usuario_rol del modelo de datos)
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Indica si ya existe un usuario con el correo, el correo se guarda siempre en minúsculas por lo que
     * quien llama debe normalizarlo antes de consultar
     *
     * @param email correo normalizado
     * @return true si el correo ya está registrado
     */
    boolean existsByEmail(String email);

    /**
     * Búsqueda paginada de usuarios con filtros opcionales combinables (RF-MS01-07, formulario F-07), un
     * filtro null no se aplica
     *
     * @param correo texto que debe contener el correo, sin distinguir mayúsculas, o null
     * @param estado estado exacto, o null
     * @param rol rol que el usuario debe tener entre los suyos, o null
     * @param pageable página, tamaño y orden solicitados
     * @return la página de usuarios que cumplen todos los filtros
     */
    @Query("SELECT DISTINCT u FROM UserEntity u WHERE "
            + "(:correo IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:correo AS string), '%'))) AND "
            + "(:estado IS NULL OR u.state = :estado) AND "
            + "(:rol IS NULL OR :rol MEMBER OF u.roles)")
    Page<UserEntity> searchUsers(@Param("correo") String correo, @Param("estado") UserState estado,
            @Param("rol") UserRole rol, Pageable pageable);

    /**
     * Consulta de autenticación (RF-MS01-02), devuelve el usuario por correo junto con sus roles en una
     * sola consulta, el correo se guarda siempre en minúsculas, por eso quien llama debe normalizarlo y la
     * búsqueda por igualdad aprovecha el índice único del correo
     *
     * @param email correo normalizado
     * @return el usuario con sus roles, o vacío si no existe
     */
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmail(String email);

    /**
     * Devuelve a los usuarios con los ids especificados y bloquea sus filas hasta que termine la
     * transacción, se usa en las operaciones masivas sobre usuarios para que se apliquen de forma atómica
     *
     * @param ids identificadores de los usuarios
     * @return los usuarios ordenados por id
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<UserEntity> findAllByIdInOrderById(Collection<UUID> ids);

    /**
     * Cuenta los usuarios que tienen un rol
     *
     * @param role rol que se busca
     * @return cantidad de usuarios que tienen ese rol
     */
    @Query("select count(u) from UserEntity u join u.roles r where r = :role")
    long countWithRole(@Param("role") UserRole role);
}
