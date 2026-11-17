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
 * Access to the users and their roles (tables users and user_roles of the data model)
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

        /**
     * Tells whether a user with the email already exists, the email is always stored in lowercase so the caller must
     * normalize it before querying
     *
     * @param email normalized email
     * @return true if the email is already registered
     */
    boolean existsByEmail(String email);

        /**
     * Paginated search of users with optional combinable filters (RF-MS01-07, form F-07), a null filter is not applied
     *
     * @param correo text that the email must contain, case insensitive, or null
     * @param estado exact status, or null
     * @param rol role that the user must have among its own, or null
     * @param pageable requested page, size and order
     * @return the page of users that meet all the filters
     */
    @Query("SELECT DISTINCT u FROM UserEntity u WHERE "
            + "(:correo IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:correo AS string), '%'))) AND "
            + "(:estado IS NULL OR u.state = :estado) AND "
            + "(:rol IS NULL OR EXISTS (SELECT r FROM u.roles r WHERE r.name = :rol))")
    Page<UserEntity> searchUsers(@Param("correo") String correo, @Param("estado") UserState estado,
            @Param("rol") String rol, Pageable pageable);

        /**
     * Internal query of the users that have a role (RF-MS01-09), regardless of their status
     *
     * @param rol role that the user must have among its own
     * @param pageable requested page, size and order
     * @return the page of users with that role
     */
    @Query("SELECT u FROM UserEntity u WHERE EXISTS (SELECT r FROM u.roles r WHERE r.name = :rol)")
    Page<UserEntity> findByRole(@Param("rol") String rol, Pageable pageable);

        /**
     * Internal query of the users that have a role and registered up to an instant (RF-MS01-09),
     * the cut allows going through all the pages over the same set even if new users register
     *
     * @param rol role that the user must have among its own
     * @param creadoAntes cut instant, included
     * @param pageable requested page, size and order
     * @return the page of users with that role created up to the cut
     */
    @Query("SELECT u FROM UserEntity u WHERE EXISTS (SELECT r FROM u.roles r WHERE r.name = :rol) "
            + "AND u.createdAt <= :creadoAntes")
    Page<UserEntity> findByRoleCreatedUpTo(@Param("rol") String rol, @Param("creadoAntes") Instant creadoAntes,
            Pageable pageable);

        /**
     * Authentication query (RF-MS01-02), returns the user by email together with its roles and the permissions of each
     * role in a single query, the email is always stored in lowercase, so the caller must normalize it and the search
     * by equality takes advantage of the unique index of the email
     *
     * @param email normalized email
     * @return the user with its roles and permissions, or empty if it does not exist
     */
    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<UserEntity> findByEmail(String email);

        /**
     * Returns the users with the specified ids and locks their rows until the transaction ends, it is used in the bulk
     * operations over users so that they are applied atomically
     *
     * @param ids identifiers of the users
     * @return the users sorted by id
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<UserEntity> findAllByIdInOrderById(Collection<UUID> ids);

        /**
     * Counts the users that have a role
     *
     * @param role name of the role being searched
     * @return number of users that have that role
     */
    @Query("select count(u) from UserEntity u join u.roles r where r.name = :role")
    long countWithRole(@Param("role") String role);

        /**
     * Counts the users whose only role is the indicated one
     *
     * @param role name of the role
     * @return number of users that have only that role
     */
    @Query(value = "SELECT count(*) FROM user_roles ur "
            + "WHERE ur.role_id = (SELECT id FROM roles WHERE name = :role) AND NOT EXISTS ("
            + "SELECT 1 FROM user_roles other WHERE other.user_id = ur.user_id "
            + "AND other.role_id <> ur.role_id)", nativeQuery = true)
    long countWithOnlyRole(@Param("role") String role);

        /**
     * Writes the session cut of all the users that have a role (RF-MS01-11)
     *
     * @param role name of the role
     * @param cut cut instant, truncated to seconds
     * @return number of marked users
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE users SET valid_from = :cut WHERE id IN ("
            + "SELECT user_id FROM user_roles WHERE role_id = (SELECT id FROM roles WHERE name = :role))",
            nativeQuery = true)
    int markSessionCutByRole(@Param("role") String role, @Param("cut") Instant cut);

        /**
     * Changes the role of those who have only the indicated role to a fallback role, it is used when deleting a role
     *
     * @param role name of the role being deleted
     * @param fallback name of the role that replaces the deleted one
     * @return number of changed assignments
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE user_roles SET role_id = (SELECT id FROM roles WHERE name = :fallback) "
            + "WHERE role_id = (SELECT id FROM roles WHERE name = :role) AND NOT EXISTS ("
            + "SELECT 1 FROM user_roles other WHERE other.user_id = user_roles.user_id "
            + "AND other.role_id <> user_roles.role_id)", nativeQuery = true)
    int replaceOnlyRole(@Param("role") String role, @Param("fallback") String fallback);

        /**
     * Removes a role from all the users that have it, it is used when deleting a role after reassigning those who had
     * no other
     *
     * @param role name of the role being removed
     * @return number of deleted assignments
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM user_roles WHERE role_id = (SELECT id FROM roles WHERE name = :role)",
            nativeQuery = true)
    int removeRoleFromAll(@Param("role") String role);

        /**
     * Returns the users with a session cut within the window of the internal query (RF-MS01-11)
     *
     * @param since instant from which the cut is considered in force, included
     * @return id and cut of each user, from the oldest cut to the most recent and then by id
     */
    @Query("SELECT u.id AS id, u.validFrom AS validFrom FROM UserEntity u "
            + "WHERE u.validFrom IS NOT NULL AND u.validFrom >= :since ORDER BY u.validFrom, u.id")
    List<UserSessionCut> findWithSessionCutSince(@Param("since") Instant since);

        /**
     * Session cut of a user
     */
    interface UserSessionCut {

                /**
         * Returns the id of the user
         *
         * @return the id of the user
         */
        UUID getId();

                /**
         * Returns the instant of the cut
         *
         * @return the instant from which the credentials of the user are valid
         */
        Instant getValidFrom();
    }
}
