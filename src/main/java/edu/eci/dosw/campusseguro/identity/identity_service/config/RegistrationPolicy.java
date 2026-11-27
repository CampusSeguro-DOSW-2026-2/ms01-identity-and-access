package edu.eci.dosw.campusseguro.identity.identity_service.config;

/**
 * Policy of the verification codes of the registration (RF-MS01-01), it is adjusted with environment variables and
 * validated when the application starts
 *
 * @param codeTtlMinutes minutes of validity of a code since it is sent, minimum 1
 * @param maxAttempts failed attempts allowed per code before invalidating it, minimum 1
 * @param resendCooldownSeconds seconds that must pass between two codes of the same email, 0 disables the wait
 * @param maxCodesPerDay codes that can be sent to the same email in a window of one day, minimum 1
 */
public record RegistrationPolicy(int codeTtlMinutes, int maxAttempts, int resendCooldownSeconds,
        int maxCodesPerDay) {

        /**
     * Rejects values that would leave the registration unusable or without any protection
     *
     * @throws IllegalArgumentException if the validity, the attempts or the daily limit are less than 1 or the wait is
     *         negative
     */
    public RegistrationPolicy {
        if (codeTtlMinutes < 1) {
            throw new IllegalArgumentException("registration.code-ttl-minutes must be at least 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("registration.max-attempts must be at least 1");
        }
        if (resendCooldownSeconds < 0) {
            throw new IllegalArgumentException("registration.resend-cooldown-seconds must not be negative");
        }
        if (maxCodesPerDay < 1) {
            throw new IllegalArgumentException("registration.max-codes-per-day must be at least 1");
        }
    }
}
