package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PasswordResetCodeEntity;
import jakarta.persistence.LockModeType;

/**
 * Access to the password recovery codes (table password_reset_codes of the data model)
 */
@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCodeEntity, UUID> {

        /**
     * Returns the most recent code of a user whether or not it is still valid, it is used to respect the minimum time
     * between one code request and the next
     *
     * @param userId id of the user that owns the codes
     * @return the last code generated for the user, or empty if none was ever generated
     */
    Optional<PasswordResetCodeEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

        /**
     * Returns the most recent code of a user that has not been used yet and locks its row until the transaction ends,
     * so simultaneous verification attempts are counted one by one and cannot be used to guess the code in parallel,
     * the validity by time is evaluated by the caller
     *
     * @param userId id of the user that owns the codes
     * @return the last unused code of the user, or empty if it has none
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetCodeEntity> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

        /**
     * Invalidates all the unused codes of a user by marking them as used, it is run before generating a new one so that
     * only one valid code exists per user (RN-MS01-21)
     *
     * @param userId id of the user that owns the codes
     * @param usedAt instant at which the codes are invalidated
     * @return the number of invalidated codes
     */
    @Modifying
    @Query("update PasswordResetCodeEntity c set c.usedAt = :usedAt "
            + "where c.userId = :userId and c.usedAt is null")
    int invalidateUnused(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);
}
