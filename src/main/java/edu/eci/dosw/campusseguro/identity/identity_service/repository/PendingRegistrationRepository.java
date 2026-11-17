package edu.eci.dosw.campusseguro.identity.identity_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.PendingRegistrationEntity;
import jakarta.persistence.LockModeType;

/**
 * Access to the registrations that wait for the verification of their email (table pending_registrations of the data
 * model, RF-MS01-01)
 *
 * The writes that issue a code are single native statements: the minimum wait between two codes and the daily limit
 * of codes of the same email are evaluated by the database in the same statement that writes the row, so two
 * simultaneous requests can never send more codes than allowed. All the times are taken from the clock of the database
 */
@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistrationEntity, String> {

    String DAY_WINDOW_ENDED = "window_started_at <= now() - interval '1 day'";

        /**
     * Creates the pending registration of an email or overwrites the previous one with the new data and a new code
     * (INSERT ... ON CONFLICT DO UPDATE), the overwrite only happens if the minimum wait since the last code has passed
     * and the email has not reached the daily limit of codes
     *
     * @param email email in lowercase, key of the registration
     * @param firstName first name
     * @param secondName second name, may be null
     * @param firstLastName first lastname
     * @param secondLastName second lastname, may be null
     * @param passwordHash hash of the chosen password
     * @param codeHash hash of the new code
     * @param tokenHash hash of the registration token given to whoever made this registration
     * @param ttlMinutes minutes of validity of the code
     * @param cooldownSeconds minimum wait in seconds since the last code of the email
     * @param maxCodesPerDay codes allowed per email in a window of one day
     * @return 1 if the row was created or overwritten and the code must be sent, 0 if the wait or the daily limit
     *         stopped it
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO pending_registrations (email, first_name, second_name, first_lastname, "
            + "second_lastname, password_hash, code_hash, request_token_hash, attempts, expires_at, code_sent_at, "
            + "codes_sent, window_started_at) "
            + "VALUES (:email, :firstName, CAST(:secondName AS VARCHAR), :firstLastName, "
            + "CAST(:secondLastName AS VARCHAR), :passwordHash, :codeHash, :tokenHash, 0, "
            + "now() + :ttlMinutes * interval '1 minute', now(), 1, now()) "
            + "ON CONFLICT (email) DO UPDATE SET first_name = EXCLUDED.first_name, "
            + "second_name = EXCLUDED.second_name, first_lastname = EXCLUDED.first_lastname, "
            + "second_lastname = EXCLUDED.second_lastname, password_hash = EXCLUDED.password_hash, "
            + "code_hash = EXCLUDED.code_hash, request_token_hash = EXCLUDED.request_token_hash, attempts = 0, "
            + "expires_at = EXCLUDED.expires_at, "
            + "code_sent_at = EXCLUDED.code_sent_at, "
            + "codes_sent = CASE WHEN pending_registrations." + DAY_WINDOW_ENDED + " THEN 1 "
            + "ELSE pending_registrations.codes_sent + 1 END, "
            + "window_started_at = CASE WHEN pending_registrations." + DAY_WINDOW_ENDED + " THEN now() "
            + "ELSE pending_registrations.window_started_at END "
            + "WHERE pending_registrations.code_sent_at <= now() - :cooldownSeconds * interval '1 second' "
            + "AND (pending_registrations." + DAY_WINDOW_ENDED
            + " OR pending_registrations.codes_sent < :maxCodesPerDay)",
            nativeQuery = true)
    int upsertWithNewCode(@Param("email") String email, @Param("firstName") String firstName,
            @Param("secondName") String secondName, @Param("firstLastName") String firstLastName,
            @Param("secondLastName") String secondLastName, @Param("passwordHash") String passwordHash,
            @Param("codeHash") String codeHash, @Param("tokenHash") String tokenHash,
            @Param("ttlMinutes") int ttlMinutes, @Param("cooldownSeconds") int cooldownSeconds,
            @Param("maxCodesPerDay") int maxCodesPerDay);

        /**
     * Gives a new code to an existing pending registration without changing its data, only to whoever made it (same
     * registration token), if the minimum wait since the last code has passed and the email has not reached the daily
     * limit of codes
     *
     * @param email email in lowercase, key of the registration
     * @param tokenHash hash of the registration token received from the client
     * @param codeHash hash of the new code
     * @param ttlMinutes minutes of validity of the code
     * @param cooldownSeconds minimum wait in seconds since the last code of the email
     * @param maxCodesPerDay codes allowed per email in a window of one day
     * @return 1 if the code was replaced and must be sent, 0 if there is no pending registration with that token or
     *         the wait or the daily limit stopped it
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE pending_registrations SET code_hash = :codeHash, attempts = 0, "
            + "expires_at = now() + :ttlMinutes * interval '1 minute', code_sent_at = now(), "
            + "codes_sent = CASE WHEN " + DAY_WINDOW_ENDED + " THEN 1 ELSE codes_sent + 1 END, "
            + "window_started_at = CASE WHEN " + DAY_WINDOW_ENDED + " THEN now() ELSE window_started_at END "
            + "WHERE email = :email AND request_token_hash = :tokenHash "
            + "AND code_sent_at <= now() - :cooldownSeconds * interval '1 second' "
            + "AND (" + DAY_WINDOW_ENDED + " OR codes_sent < :maxCodesPerDay)",
            nativeQuery = true)
    int replaceCode(@Param("email") String email, @Param("tokenHash") String tokenHash,
            @Param("codeHash") String codeHash,
            @Param("ttlMinutes") int ttlMinutes, @Param("cooldownSeconds") int cooldownSeconds,
            @Param("maxCodesPerDay") int maxCodesPerDay);

        /**
     * Returns the pending registration of an email and locks its row until the transaction ends, so simultaneous
     * verification attempts are counted one by one and cannot be used to guess the code in parallel
     *
     * @param email email in lowercase
     * @return the pending registration, or empty if there is none
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingRegistrationEntity> findLockedByEmail(String email);

        /**
     * Deletes the pending registrations whose code expired and whose window of the daily limit already ended, the
     * row of an expired code is kept until then so that deleting it does not reset the daily limit of the email
     *
     * @return number of deleted registrations
     */
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM pending_registrations WHERE expires_at < now() AND " + DAY_WINDOW_ENDED,
            nativeQuery = true)
    int deleteExpired();
}
