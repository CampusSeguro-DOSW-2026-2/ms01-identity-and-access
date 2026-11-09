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
 * Cuerpo de PATCH /api/v1/users/status (RF-MS01-07, formulario F-08), cualquier campo fuera del
 * contrato se rechaza (RG-04)
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
     * Devuelve los ids de los usuarios cuyo estado se cambia
     *
     * @return los ids recibidos como texto, null si no se enviaron
     */
    public List<String> getIds() {
        return ids;
    }

    /**
     * Coloca los ids de los usuarios cuyo estado se cambia, cada uno recortado según RG-01
     *
     * @param ids ids de los usuarios como texto
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
     * Devuelve el nuevo estado solicitado
     *
     * @return el nombre del estado recibido, null si no se envió
     */
    public String getEstado() {
        return estado;
    }

    /**
     * Coloca el nuevo estado solicitado, recortado según RG-01
     *
     * @param estado nombre del estado recibido
     */
    public void setEstado(String estado) {
        this.estado = Texts.trimToNull(estado);
    }

    /**
     * Convierte los ids recibidos en UUID, sin repetidos y en el orden en que llegaron, solo se debe
     * llamar después de validar la petición
     *
     * @return conjunto ordenado de identificadores de usuario
     */
    public Set<UUID> toUserIds() {
        Set<UUID> parsed = new LinkedHashSet<>();
        for (String id : ids) {
            parsed.add(UUID.fromString(id));
        }
        return parsed;
    }

    /**
     * Convierte el estado recibido en su valor del catálogo, solo se debe llamar después de validar la
     * petición
     *
     * @return el estado solicitado
     */
    public UserState toState() {
        return UserState.valueOf(estado);
    }
}
