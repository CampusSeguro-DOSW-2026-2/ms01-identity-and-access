package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PermissionEntity;

/**
 * Acceso al catálogo de permisos (tabla permisos del modelo de datos)
 */
@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, UUID> {

    /**
     * Devuelve los permisos cuyo código empieza con un prefijo, se usa para leer los permisos de un módulo
     *
     * @param prefix prefijo del código, por ejemplo ms01:
     * @return los permisos del módulo, sin un orden definido
     */
    List<PermissionEntity> findAllByNameStartingWith(String prefix);

    /**
     * Borra un permiso del catálogo, la clave foránea de rol_permiso lo quita de todos los roles que lo
     * tenían, quien llama debe haber registrado antes el corte de sesión de esos roles
     *
     * @param name código del permiso que se borra
     * @return cantidad de permisos borrados
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PermissionEntity p WHERE p.name = :name")
    int deleteByCode(@Param("name") String name);
}
