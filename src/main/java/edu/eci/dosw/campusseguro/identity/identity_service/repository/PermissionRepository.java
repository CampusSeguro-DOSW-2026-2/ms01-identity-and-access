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
 * Access to the permission catalog (table permissions of the data model)
 */
@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, UUID> {

        /**
     * Returns the permissions whose code starts with a prefix, it is used to read the permissions of a module
     *
     * @param prefix prefix of the code, for example ms01:
     * @return the permissions of the module, in no defined order
     */
    List<PermissionEntity> findAllByNameStartingWith(String prefix);

        /**
     * Deletes a permission from the catalog, the foreign key of role_permissions removes it from all the roles that had
     * it, the caller must have recorded the session cut of those roles before
     *
     * @param name code of the permission that is deleted
     * @return number of deleted permissions
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PermissionEntity p WHERE p.name = :name")
    int deleteByCode(@Param("name") String name);
}
