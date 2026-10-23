package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity>{

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

    /**
     * Consulta de autenticacion (RF-MS01-02): usuario por correo junto con sus roles en una sola
     * consulta. El correo se guarda siempre en minusculas (CHECK correo = lower(correo)), por eso
     * quien llama debe normalizarlo y la busqueda por igualdad aprovecha el indice unico
     * uq_users_correo en lugar de un upper()/lower() que lo invalidaria.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmail(String email);

}
