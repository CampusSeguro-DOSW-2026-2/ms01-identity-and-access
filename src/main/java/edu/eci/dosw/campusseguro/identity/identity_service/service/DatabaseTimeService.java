package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;

/**
 * Single clock of MS01 (RF-MS01-11): the database time, the same for all the instances of the service, with which the
 * iat of the tokens and the session cuts are set
 */
public interface DatabaseTimeService {

        /**
     * Queries the current time of the database, truncated to seconds
     *
     * @return the current instant, without a fraction of a second
     * @throws IllegalStateException if the database does not return the time
     */
    Instant now();
}
