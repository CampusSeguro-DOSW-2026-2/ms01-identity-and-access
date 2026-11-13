package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;

/**
 * Solicitud de creación de un rol ya validada y normalizada (formulario F-10)
 *
 * @param name nombre del rol en mayúsculas
 * @param description descripción del rol, recortada
 * @param permissions códigos de permiso del catálogo, ordenados y sin repetidos
 */
public record CreateRoleCommand(String name, String description, List<String> permissions) {
}
