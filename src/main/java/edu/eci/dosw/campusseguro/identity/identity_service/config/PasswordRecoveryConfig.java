package edu.eci.dosw.campusseguro.identity.identity_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration of the password recovery (RF-MS01-08), it is adjusted with environment variables (or with the
 * recovery.* properties)
 *
 * RECOVERY_CODE_TTL_MINUTES minutes of validity of the code, 15 by default (RN-MS01-21) RECOVERY_MAX_ATTEMPTS failed
 * attempts per code before invalidating it, 5 by default RECOVERY_RESEND_COOLDOWN_SECONDS wait between two codes of the
 * same user, 60 by default, 0 disables it
 */
@Configuration
public class PasswordRecoveryConfig {

        /**
     * Creates the policy of the recovery codes from the configuration
     *
     * @param codeTtlMinutes minutes of validity of a code
     * @param maxAttempts failed attempts allowed per code
     * @param resendCooldownSeconds minimum wait between two codes of the same user
     * @return the validated policy
     */
    @Bean
    public RecoveryPolicy recoveryPolicy(
            @Value("${recovery.code-ttl-minutes:15}") int codeTtlMinutes,
            @Value("${recovery.max-attempts:5}") int maxAttempts,
            @Value("${recovery.resend-cooldown-seconds:60}") int resendCooldownSeconds) {
        return new RecoveryPolicy(codeTtlMinutes, maxAttempts, resendCooldownSeconds);
    }
}
