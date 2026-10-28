package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserEntity;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

/**
 * Datos de un usuario (esquema User del contrato), se usa en la consulta y actualización del perfil, en
 * la consulta por id y en los elementos del listado, el segundo nombre y el segundo apellido se omiten
 * cuando están vacíos
 *
 * @param id id del usuario
 * @param firstName primer nombre
 * @param secondName segundo nombre, omitido si no tiene
 * @param firstLastName primer apellido
 * @param secondLastName segundo apellido, omitido si no tiene
 * @param email correo institucional
 * @param roles roles del usuario, en orden alfabético
 * @param state estado de la cuenta
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileResponse(
        UUID id,
        @JsonProperty("primer_nombre") String firstName,
        @JsonProperty("segundo_nombre") String secondName,
        @JsonProperty("primer_apellido") String firstLastName,
        @JsonProperty("segundo_apellido") String secondLastName,
        @JsonProperty("correo") String email,
        List<UserRole> roles,
        @JsonProperty("estado") UserState state) {

    /**
     * Construye la respuesta a partir del usuario guardado
     *
     * @param entity usuario guardado
     * @return la respuesta con todos los datos del usuario y sus roles ordenados
     */
    public static UserProfileResponse fromEntity(UserEntity entity) {
        return new UserProfileResponse(
                entity.getId(),
                entity.getFirstName(),
                entity.getSecondName(),
                entity.getFirstLastName(),
                entity.getSecondLastName(),
                entity.getEmail(),
                UserRole.sortedByName(entity.getRoles()),
                entity.getState());
    }
}
