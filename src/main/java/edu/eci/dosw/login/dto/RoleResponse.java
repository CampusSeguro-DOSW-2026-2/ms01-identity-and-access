package edu.eci.dosw.login.dto;

import edu.eci.dosw.login.entity.UserRol;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Rol disponible en el sistema. El catalogo proviene del enum {@link UserRol},
 * que replica el constraint ckk_rol de la tabla users.
 */
@Schema(description = "Rol disponible en el sistema")
public record RoleResponse(

        @Schema(description = "Codigo del rol usado en la API", example = "ESTUDIANTE")
        String code,

        @Schema(description = "Nombre legible del rol", example = "Estudiante")
        String name
) {

    public static RoleResponse from(UserRol rol) {
        return new RoleResponse(rol.name(), rol.getDisplayName());
    }
}