package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import edu.eci.dosw.campusseguro.identity.identity_service.service.DatabaseTimeServiceImpl;

/**
 * RF-MS01-11: single clock of MS01 based on the database time
 */
@ExtendWith(MockitoExtension.class)
class DatabaseTimeServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

        /**
     * The database time arrives in seconds since the epoch and is converted into an instant without a fraction
     */
    @Test
    void now_convertsTheDatabaseEpochSecondsToAnInstant() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1_794_505_530L);

        Instant now = new DatabaseTimeServiceImpl(jdbcTemplate).now();

        assertEquals(Instant.ofEpochSecond(1_794_505_530L), now);
        assertEquals(0, now.getNano());
    }

        /**
     * The query uses the real clock of the database (clock_timestamp, not the start of the transaction) and truncates
     * to seconds
     */
    @Test
    void now_queriesTheRealClockOfTheDatabaseTruncatedToSeconds() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);

        new DatabaseTimeServiceImpl(jdbcTemplate).now();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class));
        assertTrue(sql.getValue().contains("clock_timestamp()"), sql.getValue());
        assertTrue(sql.getValue().contains("floor("), sql.getValue());
        assertTrue(sql.getValue().contains("epoch"), sql.getValue());
    }

        /**
     * Each call queries the database: it does not keep the time of the previous call
     */
    @Test
    void now_queriesTheDatabaseOnEveryCall() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(100L, 101L);
        DatabaseTimeServiceImpl service = new DatabaseTimeServiceImpl(jdbcTemplate);

        assertEquals(Instant.ofEpochSecond(100), service.now());
        assertEquals(Instant.ofEpochSecond(101), service.now());
    }

        /**
     * If the database does not return the time an IllegalStateException is thrown
     */
    @Test
    void now_nullResult_throwsIllegalState() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);
        DatabaseTimeServiceImpl service = new DatabaseTimeServiceImpl(jdbcTemplate);

        IllegalStateException error = assertThrows(IllegalStateException.class, service::now);

        assertEquals("The database did not return the current time.", error.getMessage());
    }

        /**
     * If the database fails the error is propagated without inventing a time
     */
    @Test
    void now_databaseFailure_isPropagated() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("no connection"));
        DatabaseTimeServiceImpl service = new DatabaseTimeServiceImpl(jdbcTemplate);

        assertThrows(DataAccessResourceFailureException.class, service::now);
    }
}
