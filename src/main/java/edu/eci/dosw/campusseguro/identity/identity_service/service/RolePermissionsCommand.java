package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

/**
 * Solicitud de cambio de los permisos de un rol ya validada (formulario F-12)
 *
 * @param permissions códigos de permiso del catálogo, ordenados y sin repetidos
 */
public record RolePermissionsCommand(List<String> permissions) {
}
