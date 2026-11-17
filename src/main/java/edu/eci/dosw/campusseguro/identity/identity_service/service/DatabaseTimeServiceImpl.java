package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Single clock of MS01 based on the database time (RF-MS01-11)
 *
 * With several instances of MS01 the iat of a token and the session cut of a change come from the same clock, so the
 * comparison done by the Gateway does not depend on the synchronization between the instances
 */
@Service
public class DatabaseTimeServiceImpl implements DatabaseTimeService {

    private static final String CURRENT_SECOND_QUERY =
            "SELECT CAST(floor(extract(epoch FROM clock_timestamp())) AS BIGINT)";

    private final JdbcTemplate jdbcTemplate;

        /**
     * Creates the service with its access to the database
     *
     * @param jdbcTemplate JDBC access with which the time is queried
     */
    public DatabaseTimeServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

        /**
     * Queries the current time of the database, truncated to seconds
     *
     * @return the current instant, without a fraction of a second
     * @throws IllegalStateException if the database does not return the time
     */
    @Override
    public Instant now() {
        Long epochSecond = jdbcTemplate.queryForObject(CURRENT_SECOND_QUERY, Long.class);
        if (epochSecond == null) {
            throw new IllegalStateException("The database did not return the current time.");
        }
        return Instant.ofEpochSecond(epochSecond);
    }
}
