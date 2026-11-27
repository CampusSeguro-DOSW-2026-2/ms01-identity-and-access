package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of PATCH /api/v1/users/status (RF-MS01-07, form F-08), any field outside the contract is rejected (RG-04)
 */
public class ChangeStatusRequest extends StrictRequest {

    public static final String MISSING_USERS_MESSAGE = "Seleccione al menos un usuario.";
    public static final String INVALID_USER_ID_MESSAGE = "Identificador de usuario no válido.";
    public static final String INVALID_STATE_MESSAGE = "Estado no válido.";

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @JsonProperty("ids")
    @NotEmpty(message = MISSING_USERS_MESSAGE)
    private List<@NotNull(message = INVALID_USER_ID_MESSAGE)
            @Pattern(regexp = UUID_PATTERN, message = INVALID_USER_ID_MESSAGE) String> ids;

    @JsonProperty("estado")
    @NotNull(message = INVALID_STATE_MESSAGE)
    @Pattern(regexp = "^(ACTIVO|INACTIVO)$", message = INVALID_STATE_MESSAGE)
    private String estado;

        /**
     * Returns the ids of the users whose status is changed
     *
     * @return the ids received as text, null if they were not sent
     */
    public List<String> getIds() {
        return ids;
    }

        /**
     * Sets the ids of the users whose status is changed, each one trimmed according to RG-01
     *
     * @param ids ids of the users as text
     */
    public void setIds(List<String> ids) {
        if (ids == null) {
            this.ids = null;
            return;
        }
        List<String> trimmed = new ArrayList<>();
        for (String id : ids) {
            trimmed.add(Texts.trimToNull(id));
        }
        this.ids = trimmed;
    }

        /**
     * Returns the new requested status
     *
     * @return the name of the status received, null if it was not sent
     */
    public String getEstado() {
        return estado;
    }

        /**
     * Sets the new requested status, trimmed according to RG-01
     *
     * @param estado name of the status received
     */
    public void setEstado(String estado) {
        this.estado = Texts.trimToNull(estado);
    }

        /**
     * Converts the received ids to UUID, without repeated ones and in the order they arrived, it must be called only
     * after validating the request
     *
     * @return ordered set of user identifiers
     */
    public Set<UUID> toUserIds() {
        Set<UUID> parsed = new LinkedHashSet<>();
        for (String id : ids) {
            parsed.add(UUID.fromString(id));
        }
        return parsed;
    }

        /**
     * Converts the received status to its catalog value, it must be called only after validating the request
     *
     * @return the requested status
     */
    public UserState toState() {
        return UserState.valueOf(estado);
    }
}
