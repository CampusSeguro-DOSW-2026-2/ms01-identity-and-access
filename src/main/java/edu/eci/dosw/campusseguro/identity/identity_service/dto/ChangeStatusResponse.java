package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

/**
 * Response of PATCH /api/v1/users/status (RF-MS01-07), the result of the change for each user
 *
 * @param resultados result by user, in the order they were requested
 */
public record ChangeStatusResponse(List<IndividualChangeStateResponse> resultados) {

        /**
     * Builds the response with the results by user
     *
     * @param resultados result of each user
     * @return the response with the results
     */
    public static ChangeStatusResponse fromEntity(List<IndividualChangeStateResponse> resultados) {
        return new ChangeStatusResponse(resultados);
    }
}
