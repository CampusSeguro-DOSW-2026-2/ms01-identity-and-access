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
 * Access to the roles and their permissions (tables roles and role_permissions of the data model)
 */
@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

        /**
     * Returns all the roles with their permissions, sorted by name
     *
     * @return the roles in alphabetical order
     */
    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAllByOrderByNameAsc();

        /**
     * Finds a role by its name, which identifies it to the outside
     *
     * @param name name of the role, already normalized to uppercase
     * @return the role with its permissions, or empty if it does not exist
     */
    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findByName(String name);

        /**
     * Tells whether a role with the name exists
     *
     * @param name name of the role, already normalized to uppercase
     * @return true if a role with that name exists
     */
    boolean existsByName(String name);

        /**
     * Returns the roles with the received names and their permissions, the names that do not exist do not appear
     *
     * @param names role names
     * @return the roles found, in no defined order
     */
    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAllByNameIn(Collection<String> names);

        /**
     * Returns the roles with a session cut within the window of the internal query (RF-MS01-11)
     *
     * @param since instant from which the cut is considered in force, included
     * @return the roles with a cut in force, from the oldest cut to the most recent and then by name
     */
    @Query("SELECT r FROM RoleEntity r WHERE r.validFrom IS NOT NULL AND r.validFrom >= :since "
            + "ORDER BY r.validFrom, r.name")
    List<RoleEntity> findWithSessionCutSince(@Param("since") Instant since);

        /**
     * Returns the names of the roles that have a permission
     *
     * @param permission code of the permission
     * @return the role names in alphabetical order
     */
    @Query("SELECT r.name FROM RoleEntity r JOIN r.permissions p WHERE p.name = :permission ORDER BY r.name")
    List<String> findNamesByPermission(@Param("permission") String permission);

        /**
     * Returns the roles that have a permission, only their own columns are used (name and description)
     *
     * @param permission code of the permission
     * @return the roles in alphabetical order
     */
    @Query("SELECT r FROM RoleEntity r JOIN r.permissions p WHERE p.name = :permission ORDER BY r.name")
    List<RoleEntity> findWithPermission(@Param("permission") String permission);

        /**
     * Writes the session cut of all the roles that have a permission (RF-MS01-11), it is used before removing the
     * permission from the catalog
     *
     * @param permission code of the permission
     * @param cut cut instant, truncated to seconds
     * @return number of marked roles
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE roles SET valid_from = :cut WHERE id IN ("
            + "SELECT rp.role_id FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id "
            + "WHERE p.name = :permission)", nativeQuery = true)
    int markSessionCutByPermission(@Param("permission") String permission, @Param("cut") Instant cut);
}
