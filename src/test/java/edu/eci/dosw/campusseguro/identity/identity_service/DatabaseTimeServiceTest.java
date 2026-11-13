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
 * RF-MS01-11: reloj único de MS01 basado en la hora de la base de datos
 */
@ExtendWith(MockitoExtension.class)
class DatabaseTimeServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    /**
     * La hora de la base de datos llega en segundos desde la época y se convierte en un instante sin fracción
     */
    @Test
    void now_convertsTheDatabaseEpochSecondsToAnInstant() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1_794_505_530L);

        Instant now = new DatabaseTimeServiceImpl(jdbcTemplate).now();

        assertEquals(Instant.ofEpochSecond(1_794_505_530L), now);
        assertEquals(0, now.getNano());
    }

    /**
     * La consulta usa el reloj real de la base de datos (clock_timestamp, no el inicio de la transacción) y
     * trunca a segundos
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
     * Cada llamada consulta la base de datos: no guarda la hora de la llamada anterior
     */
    @Test
    void now_queriesTheDatabaseOnEveryCall() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(100L, 101L);
        DatabaseTimeServiceImpl service = new DatabaseTimeServiceImpl(jdbcTemplate);

        assertEquals(Instant.ofEpochSecond(100), service.now());
        assertEquals(Instant.ofEpochSecond(101), service.now());
    }

    /**
     * Si la base de datos no devuelve la hora se lanza IllegalStateException
     */
    @Test
    void now_nullResult_throwsIllegalState() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);
        DatabaseTimeServiceImpl service = new DatabaseTimeServiceImpl(jdbcTemplate);

        IllegalStateException error = assertThrows(IllegalStateException.class, service::now);

        assertEquals("La base de datos no devolvió la hora actual.", error.getMessage());
    }

    /**
     * Si la base de datos falla el error se propaga sin inventar una hora
     */
    @Test
    void now_databaseFailure_isPropagated() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("sin conexión"));
        DatabaseTimeServiceImpl service = new DatabaseTimeServiceImpl(jdbcTemplate);

        assertThrows(DataAccessResourceFailureException.class, service::now);
    }
}
