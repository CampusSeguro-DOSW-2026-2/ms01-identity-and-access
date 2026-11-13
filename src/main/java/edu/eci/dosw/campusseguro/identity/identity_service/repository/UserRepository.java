package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
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
            @Param("rol") String rol, Pageable pageable);

    /**
     * Consulta interna de los usuarios que tienen un rol (RF-MS01-09), sin importar su estado
     *
     * @param rol rol que el usuario debe tener entre los suyos
     * @param pageable página, tamaño y orden solicitados
     * @return la página de usuarios con ese rol
     */
    @Query("SELECT u FROM UserEntity u WHERE :rol MEMBER OF u.roles")
    Page<UserEntity> findByRole(@Param("rol") String rol, Pageable pageable);

    /**
     * Consulta interna de los usuarios que tienen un rol y se registraron hasta un instante (RF-MS01-09),
     * el corte permite recorrer todas las páginas sobre el mismo conjunto aunque se registren usuarios nuevos
     *
     * @param rol rol que el usuario debe tener entre los suyos
     * @param creadoAntes instante de corte, incluido
     * @param pageable página, tamaño y orden solicitados
     * @return la página de usuarios con ese rol creados hasta el corte
     */
    @Query("SELECT u FROM UserEntity u WHERE :rol MEMBER OF u.roles AND u.createdAt <= :creadoAntes")
    Page<UserEntity> findByRoleCreatedUpTo(@Param("rol") String rol, @Param("creadoAntes") Instant creadoAntes,
            Pageable pageable);

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
     * @param role nombre del rol que se busca
     * @return cantidad de usuarios que tienen ese rol
     */
    @Query("select count(u) from UserEntity u join u.roles r where r = :role")
    long countWithRole(@Param("role") String role);

    /**
     * Cuenta los usuarios cuyo único rol es el indicado
     *
     * @param role nombre del rol
     * @return cantidad de usuarios que tienen solo ese rol
     */
    @Query(value = "SELECT count(*) FROM usuario_rol ur WHERE ur.rol = :role AND NOT EXISTS ("
            + "SELECT 1 FROM usuario_rol other WHERE other.usuario_id = ur.usuario_id AND other.rol <> :role)",
            nativeQuery = true)
    long countWithOnlyRole(@Param("role") String role);

    /**
     * Escribe el corte de sesión de todos los usuarios que tienen un rol (RF-MS01-11)
     *
     * @param role nombre del rol
     * @param cut instante de corte, truncado a segundos
     * @return cantidad de usuarios marcados
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE usuarios SET valido_desde = :cut WHERE id IN ("
            + "SELECT usuario_id FROM usuario_rol WHERE rol = :role)", nativeQuery = true)
    int markSessionCutByRole(@Param("role") String role, @Param("cut") Instant cut);

    /**
     * Cambia el rol de quienes tienen únicamente el rol indicado por un rol de respaldo, se usa al borrar un
     * rol
     *
     * @param role nombre del rol que se borra
     * @param fallback nombre del rol que reemplaza al borrado
     * @return cantidad de asignaciones cambiadas
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE usuario_rol SET rol = :fallback WHERE rol = :role AND NOT EXISTS ("
            + "SELECT 1 FROM usuario_rol other WHERE other.usuario_id = usuario_rol.usuario_id "
            + "AND other.rol <> :role)", nativeQuery = true)
    int replaceOnlyRole(@Param("role") String role, @Param("fallback") String fallback);

    /**
     * Retira un rol a todos los usuarios que lo tienen, se usa al borrar un rol después de reasignar a quienes
     * no tenían otro
     *
     * @param role nombre del rol que se retira
     * @return cantidad de asignaciones borradas
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM usuario_rol WHERE rol = :role", nativeQuery = true)
    int removeRoleFromAll(@Param("role") String role);

    /**
     * Devuelve los usuarios con un corte de sesión dentro de la ventana de la consulta interna (RF-MS01-11)
     *
     * @param since instante desde el cual el corte se considera vigente, incluido
     * @return id y corte de cada usuario, del corte más antiguo al más reciente y luego por id
     */
    @Query("SELECT u.id AS id, u.validFrom AS validFrom FROM UserEntity u "
            + "WHERE u.validFrom IS NOT NULL AND u.validFrom >= :since ORDER BY u.validFrom, u.id")
    List<UserSessionCut> findWithSessionCutSince(@Param("since") Instant since);

    /**
     * Corte de sesión de un usuario
     */
    interface UserSessionCut {

        /**
         * Devuelve el id del usuario
         *
         * @return el id del usuario
         */
        UUID getId();

        /**
         * Devuelve el instante del corte
         *
         * @return el instante desde el cual son válidas las credenciales del usuario
         */
        Instant getValidFrom();
    }
}
