package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.eci.dosw.campusseguro.identity.identity_service.repository.PendingRegistrationRepository;

/**
 * Scheduled deletion of the pending registrations that were never verified (RF-MS01-01), so the table
 * pending_registrations does not accumulate the data of emails that never confirmed their code
 *
 * It runs every REGISTRATION_CLEANUP_INTERVAL_MINUTES (60 by default), the first time one interval after the start. A
 * failure is only logged and the next run tries again
 */
@Component
public class PendingRegistrationCleanup {

    private static final Logger log = LoggerFactory.getLogger(PendingRegistrationCleanup.class);

    private final PendingRegistrationRepository pendingRepository;

        /**
     * Creates the task with the repository of the pending registrations
     *
     * @param pendingRepository repository of the pending registrations
     */
    public PendingRegistrationCleanup(PendingRegistrationRepository pendingRepository) {
        this.pendingRepository = pendingRepository;
    }

        /**
     * Deletes the expired pending registrations whose window of the daily limit of codes already ended
     *
     * @return number of deleted registrations, 0 if the deletion failed
     */
    @Scheduled(fixedDelayString = "${registration.cleanup-interval-minutes:60}",
            initialDelayString = "${registration.cleanup-interval-minutes:60}", timeUnit = TimeUnit.MINUTES)
    public int purgeExpired() {
        try {
            int deleted = pendingRepository.deleteExpired();
            if (deleted > 0) {
                log.info("Expired pending registrations deleted: {}", deleted);
            }
            return deleted;
        } catch (RuntimeException e) {
            log.error("The expired pending registrations could not be deleted: {}", e.getClass().getSimpleName());
            return 0;
        }
    }
}
