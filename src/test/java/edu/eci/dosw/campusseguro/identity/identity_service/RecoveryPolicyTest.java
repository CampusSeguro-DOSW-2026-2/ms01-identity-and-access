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

import edu.eci.dosw.campusseguro.identity.identity_service.config.MailConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.config.PasswordRecoveryConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.config.RecoveryPolicy;

/**
 * Policy of the recovery codes (RN-MS01-21) and its configuration: validity, attempts and wait, and the executor with
 * which the emails are sent outside the request thread
 */
class RecoveryPolicyTest {

        /**
     * The valid values are kept and exposed as durations
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
     * The wait between codes can be deactivated with zero
     */
    @Test
    void policy_zeroCooldown_isAllowedAndMeansNoWait() {
        assertTrue(new RecoveryPolicy(15, 5, 0).resendCooldown().isZero());
    }

        /**
     * A validity of less than one minute leaves the recovery unusable and is rejected at startup
     */
    @Test
    void policy_ttlBelowOneMinute_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(0, 5, 60));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(-1, 5, 60));
    }

        /**
     * Zero allowed attempts leaves the recovery unusable and is rejected at startup
     */
    @Test
    void policy_attemptsBelowOne_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(15, 0, 60));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(15, -3, 60));
    }

        /**
     * A negative wait makes no sense and is rejected at startup
     */
    @Test
    void policy_negativeCooldown_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryPolicy(15, 5, -1));
    }

        /**
     * The configuration creates the policy with the received values
     */
    @Test
    void config_createsThePolicyWithTheConfiguredValues() {
        RecoveryPolicy policy = new PasswordRecoveryConfig().recoveryPolicy(20, 3, 30);

        assertEquals(new RecoveryPolicy(20, 3, 30), policy);
    }

        /**
     * The configuration rejects invalid values of the policy
     */
    @Test
    void config_invalidValues_failAtStartup() {
        PasswordRecoveryConfig config = new PasswordRecoveryConfig();

        assertThrows(IllegalArgumentException.class, () -> config.recoveryPolicy(0, 5, 60));
    }

        /**
     * The email executor runs the tasks in a thread of its own, different from the one that delivers them
     *
     * @throws Exception if the sending thread does not finish in time
     */
    @Test
    void mailExecutor_runsTheTasksOnItsOwnThread() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new MailConfig().mailExecutor();
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
     * When the sending queue is full the executor rejects the task instead of running it in the request thread, so the
     * response never waits for the mail server
     *
     * @throws Exception if the wait of the test is interrupted
     */
    @Test
    void mailExecutor_whenSaturated_rejectsInsteadOfRunningOnTheCallerThread() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new MailConfig().mailExecutor();
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
     * Waits for the latch to be released without propagating the interruption
     *
     * @param latch latch that is waited for
     */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
