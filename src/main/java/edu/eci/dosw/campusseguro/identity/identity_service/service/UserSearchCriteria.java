package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * Criterios del listado de usuarios ya validados (formulario F-07)
 *
 * @param email texto de búsqueda parcial por correo en minúsculas, null si no se filtra por correo
 * @param state estado por el que se filtra, null si se listan todos
 * @param role rol por el que se filtra, null si se listan todos
 * @param page número de página, desde 0
 * @param size tamaño de página, de 1 a 100
 */
public record UserSearchCriteria(String email, UserState state, UserRole role, int page, int size) {
}
