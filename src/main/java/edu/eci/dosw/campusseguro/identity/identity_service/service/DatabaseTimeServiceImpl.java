package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Reloj único de MS01 basado en la hora de la base de datos (RF-MS01-11)
 *
 * Con varias instancias de MS01 el iat de un token y el corte de sesión de un cambio salen del mismo reloj,
 * y así la comparación del Gateway no depende de la sincronía entre las instancias
 */
@Service
public class DatabaseTimeServiceImpl implements DatabaseTimeService {

    private static final String CURRENT_SECOND_QUERY =
            "SELECT CAST(floor(extract(epoch FROM clock_timestamp())) AS BIGINT)";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Crea el servicio con su acceso a la base de datos
     *
     * @param jdbcTemplate acceso JDBC con el que se consulta la hora
     */
    public DatabaseTimeServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Consulta la hora actual de la base de datos, truncada a segundos
     *
     * @return el instante actual, sin fracción de segundo
     * @throws IllegalStateException si la base de datos no devuelve la hora
     */
    @Override
    public Instant now() {
        Long epochSecond = jdbcTemplate.queryForObject(CURRENT_SECOND_QUERY, Long.class);
        if (epochSecond == null) {
            throw new IllegalStateException("La base de datos no devolvió la hora actual.");
        }
        return Instant.ofEpochSecond(epochSecond);
    }
}
