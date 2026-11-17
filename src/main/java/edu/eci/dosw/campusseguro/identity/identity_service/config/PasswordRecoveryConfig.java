package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

    public static final String MAIL_EXECUTOR = "mailExecutor";

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

        /**
     * Creates the executor with which the emails are sent outside the request thread, so the response time of the code
     * request does not depend on the mail server and does not reveal whether the account exists (RN-MS01-20), the
     * pending sends are completed when the application is shut down
     *
     * @return the executor of the email sends
     */
    @Bean(name = MAIL_EXECUTOR)
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
