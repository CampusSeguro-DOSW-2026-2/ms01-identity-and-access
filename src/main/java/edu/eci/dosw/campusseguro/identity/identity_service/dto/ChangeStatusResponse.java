package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;


public class ChangeStatusResponse {

    @JsonProperty("resultados")
    private List<IndividualChangeStateResponse> resultados;

    /**
     * 
     * @param resultados
     */
    public static ChangeStatusResponse fromEntity(List<IndividualChangeStateResponse> resultados) {
        ChangeStatusResponse response = new ChangeStatusResponse();
        response.setResultados(resultados);
        return response;
    }

    public List<IndividualChangeStateResponse> getResultados(){ return this.resultados; }
    public void setResultados(List<IndividualChangeStateResponse> resultados) { this.resultados = resultados; }
}
