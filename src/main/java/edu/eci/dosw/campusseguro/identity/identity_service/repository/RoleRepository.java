package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;

/**
 * Acceso a los roles y a sus permisos (tablas roles y rol_permiso del modelo de datos)
 */
@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    /**
     * Devuelve todos los roles con sus permisos, ordenados por nombre
     *
     * @return los roles en orden alfabético
     */
    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAllByOrderByNameAsc();

    /**
     * Busca un rol por su nombre, que lo identifica hacia afuera
     *
     * @param name nombre del rol, ya normalizado a mayúsculas
     * @return el rol con sus permisos, o vacío si no existe
     */
    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findByName(String name);

    /**
     * Indica si existe un rol con el nombre
     *
     * @param name nombre del rol, ya normalizado a mayúsculas
     * @return true si existe un rol con ese nombre
     */
    boolean existsByName(String name);

    /**
     * Devuelve los roles con los nombres recibidos y sus permisos, los nombres que no existen no aparecen
     *
     * @param names nombres de rol
     * @return los roles encontrados, sin un orden definido
     */
    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAllByNameIn(Collection<String> names);

    /**
     * Devuelve los roles con un corte de sesión dentro de la ventana de la consulta interna (RF-MS01-11)
     *
     * @param since instante desde el cual el corte se considera vigente, incluido
     * @return los roles con corte vigente, del corte más antiguo al más reciente y luego por nombre
     */
    @Query("SELECT r FROM RoleEntity r WHERE r.validFrom IS NOT NULL AND r.validFrom >= :since "
            + "ORDER BY r.validFrom, r.name")
    List<RoleEntity> findWithSessionCutSince(@Param("since") Instant since);

    /**
     * Devuelve los nombres de los roles que tienen un permiso
     *
     * @param permission código del permiso
     * @return los nombres de rol en orden alfabético
     */
    @Query("SELECT r.name FROM RoleEntity r JOIN r.permissions p WHERE p.name = :permission ORDER BY r.name")
    List<String> findNamesByPermission(@Param("permission") String permission);

    /**
     * Escribe el corte de sesión de todos los roles que tienen un permiso (RF-MS01-11), se usa antes de
     * retirar el permiso del catálogo
     *
     * @param permission código del permiso
     * @param cut instante de corte, truncado a segundos
     * @return cantidad de roles marcados
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE roles SET valido_desde = :cut WHERE id IN ("
            + "SELECT rp.rol_id FROM rol_permiso rp JOIN permisos p ON p.id = rp.permiso_id "
            + "WHERE p.nombre = :permission)", nativeQuery = true)
    int markSessionCutByPermission(@Param("permission") String permission, @Param("cut") Instant cut);
}
