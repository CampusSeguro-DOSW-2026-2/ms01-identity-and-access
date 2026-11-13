package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.RoleEntity;

/**
 * Acceso a los roles y a sus permisos (tabla roles del modelo de datos)
 */
@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, String> {

    /**
     * Devuelve todos los roles ordenados por nombre
     *
     * @return los roles en orden alfabético
     */
    List<RoleEntity> findAllByOrderByNameAsc();

    /**
     * Devuelve los roles con los nombres recibidos, los nombres que no existen no aparecen
     *
     * @param names nombres de rol
     * @return los roles encontrados, sin un orden definido
     */
    List<RoleEntity> findAllByNameIn(Collection<String> names);

    /**
     * Cuenta los roles que existen entre los nombres recibidos
     *
     * @param names nombres de rol sin repetir
     * @return cantidad de nombres que corresponden a un rol existente
     */
    long countByNameIn(Collection<String> names);

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
     * Cambia el nombre y la descripción de un rol, la clave foránea de usuario_rol actualiza el nombre en las
     * asignaciones, el nombre no se puede cambiar con la entidad porque es su clave
     *
     * @param currentName nombre actual del rol
     * @param newName nombre nuevo del rol
     * @param description descripción nueva del rol
     * @param updatedAt instante de la modificación
     * @return cantidad de roles modificados
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE roles SET nombre = :newName, descripcion = :description, actualizado_en = :updatedAt "
            + "WHERE nombre = :currentName", nativeQuery = true)
    int rename(@Param("currentName") String currentName, @Param("newName") String newName,
            @Param("description") String description, @Param("updatedAt") Instant updatedAt);
}
