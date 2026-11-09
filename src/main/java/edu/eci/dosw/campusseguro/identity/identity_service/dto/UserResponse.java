package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * Respuesta de POST /api/v1/auth/register (esquema RegisterResponse del contrato), nunca incluye la
 * contraseña ni su hash (RF-MS01-01)
 *
 * @param id id del usuario creado
 * @param email correo institucional
 * @param roles roles del usuario, en orden alfabético
 * @param state estado de la cuenta
 */
public record UserResponse(
        UUID id,
        @JsonProperty("correo") String email,
        List<UserRole> roles,
        @JsonProperty("estado") UserState state) {

    /**
     * Construye la respuesta a partir del usuario guardado
     *
     * @param entity usuario guardado
     * @return la respuesta con el id, el correo, los roles ordenados y el estado
     */
    public static UserResponse fromEntity(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getEmail(),
                UserRole.sortedByName(entity.getRoles()),
                entity.getState());
    }
}
