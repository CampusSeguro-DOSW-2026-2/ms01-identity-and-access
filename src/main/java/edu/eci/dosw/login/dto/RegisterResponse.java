package edu.eci.dosw.login.dto;

import edu.eci.dosw.login.entity.UserEntity;
import edu.eci.dosw.login.entity.UserRol;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Representacion publica de un usuario. Nunca expone el password.
 */
@Schema(description = "Informacion publica de un usuario")
public record RegisterResponse(

        @Schema(description = "Identificador del usuario", example = "1")
        Long id,

        @Schema(description = "Correo electronico", example = "jdoe@mail.escuelaing.edu.co")
        String email,

        @Schema(description = "Rol del usuario", example = "ESTUDIANTE")
        UserRol rol
) {

    public static RegisterResponse fromEntity(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RegisterResponse(entity.getId(), entity.getEmail(), entity.getRol());
    }
}