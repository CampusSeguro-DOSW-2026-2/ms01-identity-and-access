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
 * Acceso a los códigos de recuperación de contraseña (tabla codigos_recuperacion del modelo de datos)
 */
@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCodeEntity, UUID> {

    /**
     * Devuelve el código más reciente de un usuario sin importar si sigue vigente, se usa para respetar el
     * tiempo mínimo entre una solicitud de código y la siguiente
     *
     * @param userId id del usuario dueño de los códigos
     * @return el último código generado para el usuario, o vacío si nunca se le generó uno
     */
    Optional<PasswordResetCodeEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Devuelve el código más reciente de un usuario que todavía no se ha usado y bloquea su fila hasta que
     * termine la transacción, así los intentos de verificación simultáneos se cuentan uno a uno y no se
     * pueden usar para adivinar el código en paralelo, la vigencia por tiempo la evalúa quien llama
     *
     * @param userId id del usuario dueño de los códigos
     * @return el último código sin usar del usuario, o vacío si no tiene ninguno
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetCodeEntity> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /**
     * Invalida todos los códigos sin usar de un usuario marcándolos como usados, se ejecuta antes de generar
     * uno nuevo para que solo exista un código vigente por usuario (RN-MS01-21)
     *
     * @param userId id del usuario dueño de los códigos
     * @param usedAt instante en el que se invalidan los códigos
     * @return la cantidad de códigos invalidados
     */
    @Modifying
    @Query("update PasswordResetCodeEntity c set c.usedAt = :usedAt "
            + "where c.userId = :userId and c.usedAt is null")
    int invalidateUnused(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);
}
