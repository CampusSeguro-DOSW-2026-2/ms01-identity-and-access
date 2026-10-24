package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PasswordResetCodeEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCodeEntity, UUID> {

    Optional<PasswordResetCodeEntity> findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(UserEntity user);
    Optional<PasswordResetCodeEntity> findFirstByUserOrderByCreatedAtDesc(UserEntity user);

    List<PasswordResetCodeEntity> findByUserAndUsedAtIsNull(UserEntity user);
}