package edu.eci.dosw.campusseguro.identity.identity_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration of the verification of the registration (RF-MS01-01), it is adjusted with environment variables (or
 * with the registration.* properties)
 *
 * REGISTRATION_CODE_TTL_MINUTES minutes of validity of the code, 15 by default REGISTRATION_MAX_ATTEMPTS failed
 * attempts per code before invalidating it, 5 by default REGISTRATION_RESEND_COOLDOWN_SECONDS wait between two codes of
 * the same email, 60 by default, 0 disables it REGISTRATION_MAX_CODES_PER_DAY codes per email in a window of one day, 5
 * by default REGISTRATION_CLEANUP_INTERVAL_MINUTES period of the deletion of the expired pending registrations, 60 by
 * default
 *
 * It also enables the scheduled tasks, used by the deletion of the expired pending registrations
 */
@Configuration
@EnableScheduling
public class RegistrationConfig {

        /**
     * Creates the policy of the verification codes from the configuration
     *
     * @param codeTtlMinutes minutes of validity of a code
     * @param maxAttempts failed attempts allowed per code
     * @param resendCooldownSeconds minimum wait between two codes of the same email
     * @param maxCodesPerDay codes allowed per email in a window of one day
     * @return the validated policy
     */
    @Bean
    public RegistrationPolicy registrationPolicy(
            @Value("${registration.code-ttl-minutes:15}") int codeTtlMinutes,
            @Value("${registration.max-attempts:5}") int maxAttempts,
            @Value("${registration.resend-cooldown-seconds:60}") int resendCooldownSeconds,
            @Value("${registration.max-codes-per-day:5}") int maxCodesPerDay) {
        return new RegistrationPolicy(codeTtlMinutes, maxAttempts, resendCooldownSeconds, maxCodesPerDay);
    }
}
