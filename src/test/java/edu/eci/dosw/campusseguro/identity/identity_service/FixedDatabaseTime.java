package edu.eci.dosw.campusseguro.identity.identity_service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import edu.eci.dosw.campusseguro.identity.identity_service.service.DatabaseTimeService;

/**
 * Reloj de la base de datos de las pruebas: devuelve siempre el mismo instante, truncado a segundos como lo
 * hace la base de datos real
 */
final class FixedDatabaseTime implements DatabaseTimeService {

    private final Instant instant;

    /**
     * Crea el reloj con el instante que devolverá siempre
     *
     * @param instant instante fijo, se trunca a segundos
     */
    private FixedDatabaseTime(Instant instant) {
        this.instant = instant.truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * Crea un reloj fijo
     *
     * @param instant instante que devolverá el reloj
     * @return el reloj fijo
     */
    static DatabaseTimeService at(Instant instant) {
        return new FixedDatabaseTime(instant);
    }

    /**
     * Devuelve el instante fijo
     *
     * @return el instante configurado, sin fracción de segundo
     */
    @Override
    public Instant now() {
        return instant;
    }
}
