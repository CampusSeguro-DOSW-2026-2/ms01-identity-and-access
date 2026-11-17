package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import edu.eci.dosw.campusseguro.identity.identity_service.config.RegistrationConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.repository.PendingRegistrationRepository;
import edu.eci.dosw.campusseguro.identity.identity_service.service.PendingRegistrationCleanup;

/**
 * Tests of the scheduled deletion of the expired pending registrations (RF-MS01-01)
 */
class PendingRegistrationCleanupTest {

    private PendingRegistrationRepository pendingRepository;
    private PendingRegistrationCleanup cleanup;

        /**
     * Prepares the task with a simulated repository
     */
    @BeforeEach
    void setUp() {
        pendingRepository = mock(PendingRegistrationRepository.class);
        cleanup = new PendingRegistrationCleanup(pendingRepository);
    }

        /**
     * The task deletes the expired registrations and returns how many were deleted
     */
    @Test
    void purgeExpired_deletesTheExpiredRegistrations() {
        when(pendingRepository.deleteExpired()).thenReturn(3);

        assertEquals(3, cleanup.purgeExpired());
        verify(pendingRepository).deleteExpired();
    }

        /**
     * A failure of the database is only logged, the task does not stop and the next run tries again
     */
    @Test
    void purgeExpired_whenTheDatabaseFails_doesNotThrow() {
        when(pendingRepository.deleteExpired()).thenThrow(new QueryTimeoutException("timeout"));

        assertEquals(0, cleanup.purgeExpired());
    }

        /**
     * The task is scheduled with the configured interval in minutes and the scheduling is enabled
     *
     * @throws NoSuchMethodException if the method of the task does not exist
     */
    @Test
    void purgeExpired_isScheduledWithTheConfiguredIntervalInMinutes() throws NoSuchMethodException {
        Scheduled scheduled = PendingRegistrationCleanup.class.getMethod("purgeExpired")
                .getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("${registration.cleanup-interval-minutes:60}", scheduled.fixedDelayString());
        assertEquals("${registration.cleanup-interval-minutes:60}", scheduled.initialDelayString());
        assertEquals(TimeUnit.MINUTES, scheduled.timeUnit());
        assertNotNull(RegistrationConfig.class.getAnnotation(EnableScheduling.class));
    }
}
