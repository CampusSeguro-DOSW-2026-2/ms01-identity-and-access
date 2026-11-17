package edu.eci.dosw.campusseguro.identity.identity_service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import edu.eci.dosw.campusseguro.identity.identity_service.service.DatabaseTimeService;

/**
 * Database clock of the tests: it always returns the same instant, truncated to seconds as the real database does
 */
final class FixedDatabaseTime implements DatabaseTimeService {

    private final Instant instant;

        /**
     * Creates the clock with the instant that it will always return
     *
     * @param instant fixed instant, it is truncated to seconds
     */
    private FixedDatabaseTime(Instant instant) {
        this.instant = instant.truncatedTo(ChronoUnit.SECONDS);
    }

        /**
     * Creates a fixed clock
     *
     * @param instant instant that the clock will return
     * @return the fixed clock
     */
    static DatabaseTimeService at(Instant instant) {
        return new FixedDatabaseTime(instant);
    }

        /**
     * Returns the fixed instant
     *
     * @return the configured instant, without a fraction of a second
     */
    @Override
    public Instant now() {
        return instant;
    }
}
