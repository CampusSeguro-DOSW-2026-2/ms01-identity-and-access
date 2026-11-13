package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.SessionCutsResponse;

/**
 * Consulta de los cortes de sesión vigentes (RF-MS01-11), la usa el API Gateway para rechazar las credenciales
 * emitidas antes del corte de un usuario o de alguno de sus roles
 */
public interface SessionCutService {

    /**
     * Arma la instantánea de los cortes de sesión de los usuarios y roles dentro de la ventana de vigencia
     *
     * @return la instantánea con el instante en que se armó, la ventana y los cortes ordenados
     */
    SessionCutsResponse currentCuts();
}
