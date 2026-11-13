package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;

/**
 * Reloj único de MS01 (RF-MS01-11): la hora de la base de datos, la misma para todas las instancias del
 * servicio, con la que se fijan el iat de los tokens y los cortes de sesión
 */
public interface DatabaseTimeService {

    /**
     * Consulta la hora actual de la base de datos, truncada a segundos
     *
     * @return el instante actual, sin fracción de segundo
     * @throws IllegalStateException si la base de datos no devuelve la hora
     */
    Instant now();
}
