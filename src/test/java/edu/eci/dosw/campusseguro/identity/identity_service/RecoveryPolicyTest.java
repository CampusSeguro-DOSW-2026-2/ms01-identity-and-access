package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import edu.eci.dosw.campusseguro.identity.identity_service.config.PasswordRecoveryConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.config.RecoveryPolicy;

/**
 * Política de los códigos de recuperación (RN-MS01-21) y su configuración: vigencia, intentos y espera, y el
 * ejecutor con el que se envían los correos fuera del hilo de la petición
 */
class RecoveryPolicyTest {

    /**
     * Los valores válidos se conservan y se exponen como duraciones
     */
    @Test
    void policy_validValues_areKeptAndExposedAsDurations() {
        RecoveryPolicy policy = new RecoveryPolicy(15, 5, 60);

        assertEquals(15, policy.codeTtlMinutes());
        assertEquals(5, policy.maxAttempts());
        assertEquals(60, policy.resendCooldownSeconds());
        assertEquals(Duration.ofMinutes(15), policy.codeTtl());
        assertEquals(Duration.ofSeconds(60), policy.resendCooldown());
    }

    /**
     * La espera entre códigos se puede desactivar con cero
     */
    @Test
    void policy_zeroCooldown_isAllowedAndMeansNoWait() {
        assertTrue(new RecoveryPolicy(15, 5, 0).resendCooldown().isZero());
    }

    /**
     * Una vigencia menor que un minuto deja la recuperación inutilizable y se rechaza al arrancar
     */
    @Test
    void policy_ttlBelowOneMinute_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(0, 5, 60));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(-1, 5, 60));
    }

    /**
     * Cero intentos permitidos deja la recuperación inutilizable y se rechaza al arrancar
     */
    @Test
    void policy_attemptsBelowOne_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(15, 0, 60));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(15, -3, 60));
    }

    /**
     * Una espera negativa no tiene sentido y se rechaza al arrancar
     */
    @Test
    void policy_negativeCooldown_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(15, 5, -1));
    }

    /**
     * La configuración crea la política con los valores recibidos
     */
    @Test
    void config_createsThePolicyWithTheConfiguredValues() {
        RecoveryPolicy policy = new PasswordRecoveryConfig().recoveryPolicy(20, 3, 30);

        assertEquals(new RecoveryPolicy(20, 3, 30), policy);
    }

    /**
     * La configuración rechaza valores inválidos de la política
     */
    @Test
    void config_invalidValues_failAtStartup() {
        PasswordRecoveryConfig config = new PasswordRecoveryConfig();

        assertThrows(IllegalArgumentException.class, () -> config.recoveryPolicy(0, 5, 60));
    }

    /**
     * El ejecutor de correos ejecuta las tareas en un hilo propio, distinto del que las entrega
     *
     * @throws Exception si el hilo de envío no termina a tiempo
     */
    @Test
    void mailExecutor_runsTheTasksOnItsOwnThread() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new PasswordRecoveryConfig().mailExecutor();
        executor.initialize();
        try {
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);

            executor.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                done.countDown();
            });

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(threadName.get().startsWith("mail-"));
            assertTrue(!threadName.get().equals(Thread.currentThread().getName()));
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Cuando la cola de envíos se llena el ejecutor rechaza la tarea en lugar de ejecutarla en el hilo de la
     * petición, así la respuesta nunca espera al servidor de correo
     *
     * @throws Exception si la espera de la prueba es interrumpida
     */
    @Test
    void mailExecutor_whenSaturated_rejectsInsteadOfRunningOnTheCallerThread() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new PasswordRecoveryConfig().mailExecutor();
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
        CountDownLatch release = new CountDownLatch(1);
        try {
            Executor target = executor;
            target.execute(() -> awaitQuietly(release));
            target.execute(() -> awaitQuietly(release));

            assertThrows(RejectedExecutionException.class, () -> target.execute(() -> awaitQuietly(release)));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    /**
     * Espera a que se libere el cierre sin propagar la interrupción
     *
     * @param latch cierre que se espera
     */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
