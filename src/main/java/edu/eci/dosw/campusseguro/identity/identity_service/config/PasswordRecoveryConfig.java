package edu.eci.dosw.campusseguro.identity.identity_service.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuración de la recuperación de contraseña (RF-MS01-08), se ajusta con variables de entorno
 * (o con las propiedades recovery.*)
 *
 * RECOVERY_CODE_TTL_MINUTES        minutos de vigencia del código, 15 por defecto (RN-MS01-21)
 * RECOVERY_MAX_ATTEMPTS            intentos fallidos por código antes de invalidarlo, 5 por defecto
 * RECOVERY_RESEND_COOLDOWN_SECONDS espera entre dos códigos del mismo usuario, 60 por defecto, 0 la desactiva
 */
@Configuration
public class PasswordRecoveryConfig {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    /**
     * Crea la política de los códigos de recuperación a partir de la configuración
     *
     * @param codeTtlMinutes minutos de vigencia de un código
     * @param maxAttempts intentos fallidos permitidos por código
     * @param resendCooldownSeconds espera mínima entre dos códigos del mismo usuario
     * @return la política validada
     */
    @Bean
    public RecoveryPolicy recoveryPolicy(
            @Value("${recovery.code-ttl-minutes:15}") int codeTtlMinutes,
            @Value("${recovery.max-attempts:5}") int maxAttempts,
            @Value("${recovery.resend-cooldown-seconds:60}") int resendCooldownSeconds) {
        return new RecoveryPolicy(codeTtlMinutes, maxAttempts, resendCooldownSeconds);
    }

    /**
     * Crea el ejecutor con el que se envían los correos fuera del hilo de la petición, así el tiempo de
     * respuesta de la solicitud de código no depende del servidor de correo y no revela si la cuenta existe
     * (RN-MS01-20), los envíos pendientes se completan al apagar la aplicación
     *
     * @return el ejecutor de los envíos de correo
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
