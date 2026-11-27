package edu.eci.dosw.campusseguro.identity.identity_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-use code to reset the password (table password_reset_codes of the data model, RF-MS01-08), the code in clear
 * text is never stored: only its hash
 *
 * A code is valid as long as it has not been used and has not expired, a used code is a code that was consumed or
 * invalidated
 */
@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

        /**
     * Returns the identifier of the code
     *
     * @return the id of the code, null if it has not been saved yet
     */
    public UUID getId() {
        return this.id;
    }

        /**
     * Returns the identifier of the user that owns the code
     *
     * @return the id of the user
     */
    public UUID getUserId() {
        return this.userId;
    }

        /**
     * Sets the identifier of the user that owns the code
     *
     * @param userId id of the user
     */
    public void setUserId(UUID userId) {
        this.userId = userId;
    }

        /**
     * Returns the hash of the six-digit code
     *
     * @return the hash of the code, never the code in clear text
     */
    public String getCodeHash() {
        return this.codeHash;
    }

        /**
     * Sets the hash of the six-digit code
     *
     * @param codeHash hash of the code
     */
    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

        /**
     * Returns the number of failed attempts to verify the code
     *
     * @return the accumulated failed attempts
     */
    public int getAttempts() {
        return this.attempts;
    }

        /**
     * Sets the number of failed attempts to verify the code
     *
     * @param attempts accumulated failed attempts
     */
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

        /**
     * Returns the instant at which the code stops being valid
     *
     * @return the expiration instant
     */
    public Instant getExpiresAt() {
        return this.expiresAt;
    }

        /**
     * Sets the instant at which the code stops being valid
     *
     * @param expiresAt expiration instant
     */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

        /**
     * Returns the instant at which the code was consumed or invalidated
     *
     * @return the instant of use, null if the code has not been used
     */
    public Instant getUsedAt() {
        return this.usedAt;
    }

        /**
     * Sets the instant at which the code was consumed or invalidated
     *
     * @param usedAt instant of use, null if the code has not been used
     */
    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

        /**
     * Returns the creation instant of the code
     *
     * @return the instant at which the code was generated
     */
    public Instant getCreatedAt() {
        return this.createdAt;
    }

        /**
     * Sets the creation instant of the code
     *
     * @param createdAt instant at which the code was generated
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

        /**
     * Tells whether the code is valid at an instant, that is, whether it has not been used and has not yet expired
     *
     * @param now instant against which the validity is evaluated
     * @return true if the code has not been used and its expiration is later than the instant
     */
    public boolean isActiveAt(Instant now) {
        if (this.usedAt != null) {
            return false;
        }
        return this.expiresAt.isAfter(now);
    }
}
