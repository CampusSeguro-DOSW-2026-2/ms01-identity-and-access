package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

/**
 * Respuesta de PATCH /api/v1/users/status (RF-MS01-07), el resultado del cambio de cada usuario
 *
 * @param resultados resultado por usuario, en el orden en que se solicitaron
 */
public record ChangeStatusResponse(List<IndividualChangeStateResponse> resultados) {

    /**
     * Construye la respuesta con los resultados por usuario
     *
     * @param resultados resultado de cada usuario
     * @return la respuesta con los resultados
     */
    public static ChangeStatusResponse fromEntity(List<IndividualChangeStateResponse> resultados) {
        return new ChangeStatusResponse(resultados);
    }
}
