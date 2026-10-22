package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID>{
    
    boolean existsByEmail(String email);
    
}
