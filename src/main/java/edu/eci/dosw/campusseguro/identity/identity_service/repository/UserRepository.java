package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID>{

    boolean existsByEmail(String email);

    /**
     * Consulta de autenticacion (RF-MS01-02): usuario por correo junto con sus roles en una sola
     * consulta. El correo se guarda siempre en minusculas (CHECK correo = lower(correo)), por eso
     * quien llama debe normalizarlo y la busqueda por igualdad aprovecha el indice unico
     * uq_users_correo en lugar de un upper()/lower() que lo invalidaria.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmail(String email);

}
