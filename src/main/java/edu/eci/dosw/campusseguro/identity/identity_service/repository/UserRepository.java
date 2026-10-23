package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID>{

    boolean existsByEmail(String email);

    /**
     * 
     * @param email
     * @return
     */
    @EntityGraph(attributePaths = "roles")
    List<UserEntity> findByEmailContainingIgnoreCase(String email);

    /**
     * 
     * @param state
     * @return
     */
    @EntityGraph(attributePaths = "roles")
    List<UserEntity> findByState(UserState state);

    /**
     * 
     * @param role
     * @return
     */
    @EntityGraph(attributePaths = "roles")
    @Query("SELECT DISTINCT u FROM UserEntity u WHERE :role MEMBER OF u.roles")
    List<UserEntity> findByRoles(@Param("role") UserRole role);

    @Query("SELECT DISTINCT u FROM UserEntity u WHERE " + "(:correo IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:correo AS string), '%'))) AND " +  "(:estado IS NULL OR u.state = :estado) AND " + "(:rol IS NULL OR :rol MEMBER OF u.roles)")
    Page<UserEntity> searchUsers(@Param("correo") String correo, @Param("estado") UserState estado, @Param("rol") UserRole rol, Pageable pageable);
    /**
     * Consulta de autenticacion (RF-MS01-02): usuario por correo junto con sus roles en una sola
     * consulta. El correo se guarda siempre en minusculas (CHECK correo = lower(correo)), por eso
     * quien llama debe normalizarlo y la busqueda por igualdad aprovecha el indice unico
     * uq_users_correo en lugar de un upper()/lower() que lo invalidaria.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmail(String email);

}
