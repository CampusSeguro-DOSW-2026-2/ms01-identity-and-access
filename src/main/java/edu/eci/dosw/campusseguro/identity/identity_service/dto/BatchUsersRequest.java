package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/v1/users/batch (RF-MS01-09), cualquier campo fuera del contrato se rechaza (RG-04)
 */
public class BatchUsersRequest extends StrictRequest {

    public static final int MAX_IDS = 100;

    public static final String INVALID_LIST_MESSAGE =
            "La lista de ids es obligatoria y debe tener entre 1 y 100 elementos.";
    public static final String INVALID_ID_MESSAGE = "Cada id debe ser un UUID válido.";

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @JsonProperty("ids")
    @NotEmpty(message = INVALID_LIST_MESSAGE)
    @Size(max = MAX_IDS, message = INVALID_LIST_MESSAGE)
    private List<@NotNull(message = INVALID_ID_MESSAGE)
            @Pattern(regexp = UUID_PATTERN, message = INVALID_ID_MESSAGE) String> ids;

    /**
     * Devuelve los ids de los usuarios consultados
     *
     * @return los ids recibidos como texto, null si no se enviaron
     */
    public List<String> getIds() {
        return ids;
    }

    /**
     * Coloca los ids de los usuarios consultados, cada uno recortado según RG-01
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
}
