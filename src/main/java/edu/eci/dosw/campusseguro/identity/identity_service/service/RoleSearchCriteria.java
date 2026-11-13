package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;

/**
 * Criterios ya validados de la consulta interna de usuarios por rol (RF-MS01-09)
 *
 * @param role rol que deben tener los usuarios
 * @param createdBefore instante de corte, incluido, null si no se limita por fecha de creación
 * @param page número de página, desde 0
 * @param size tamaño de página, de 1 a 100
 */
public record RoleSearchCriteria(String role, Instant createdBefore, int page, int size) {
}
