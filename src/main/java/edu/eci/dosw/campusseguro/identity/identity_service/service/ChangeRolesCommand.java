package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;

/**
 * Solicitud de cambio de roles ya validada: sin ids repetidos, sin roles fuera del catalogo,
 * sin roles repetidos y con al menos una de las dos listas con contenido
 *
 * @param userIds usuarios
 * @param rolesToAdd roles que se agregan a cada usuario
 * @param rolesToRemove roles que se retiran a cada usuario
 */
public record ChangeRolesCommand(Set<UUID> userIds, Set<UserRole> rolesToAdd, Set<UserRole> rolesToRemove) {
}
