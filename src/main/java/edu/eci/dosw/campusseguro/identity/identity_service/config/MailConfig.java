package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration of the sending of emails, shared by the verification of the registration (RF-MS01-01) and the password
 * recovery (RF-MS01-08)
 */
@Configuration
public class MailConfig {

    public static final String MAIL_EXECUTOR = "mailExecutor";

        /**
     * Creates the executor with which the emails are sent outside the request thread, so the response time does not
     * depend on the mail server (nor on the timeout of the API Gateway) and does not reveal whether the account exists
     * (RN-MS01-20), the pending sends are completed when the application is shut down
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
