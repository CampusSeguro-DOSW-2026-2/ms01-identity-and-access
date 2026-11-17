package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.time.Duration;

/**
 * Policy of the password recovery codes (RF-MS01-08, RN-MS01-21), it is adjusted with environment variables and
 * validated when the application starts
 *
 * @param codeTtlMinutes minutes of validity of a code since it is generated, minimum 1
 * @param maxAttempts failed attempts allowed per code before invalidating it, minimum 1
 * @param resendCooldownSeconds seconds that must pass between the generation of a code and the next one, 0 disables the
 *        wait
 */
public record RecoveryPolicy(int codeTtlMinutes, int maxAttempts, int resendCooldownSeconds) {

        /**
     * Rejects values that would leave the recovery unusable or without any protection
     *
     * @throws IllegalArgumentException if the validity or the attempts are less than 1 or the wait is negative
     */
    public RecoveryPolicy {
        if (codeTtlMinutes < 1) {
            throw new IllegalArgumentException("recovery.code-ttl-minutes must be at least 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("recovery.max-attempts must be at least 1");
        }
        if (resendCooldownSeconds < 0) {
            throw new IllegalArgumentException("recovery.resend-cooldown-seconds must not be negative");
        }
    }

        /**
     * Returns the validity of a code as a duration
     *
     * @return the time a code remains valid
     */
    public Duration codeTtl() {
        return Duration.ofMinutes(codeTtlMinutes);
    }

        /**
     * Returns the minimum time between the generation of two codes of the same user
     *
     * @return the wait between requests, zero if it is disabled
     */
    public Duration resendCooldown() {
        return Duration.ofSeconds(resendCooldownSeconds);
    }
}
