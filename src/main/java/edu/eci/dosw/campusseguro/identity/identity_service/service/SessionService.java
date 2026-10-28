package edu.eci.dosw.campusseguro.identity.identity_service.service;

import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;

/**
 * Cierre de sesión del usuario autenticado (RF-MS01-04)
 */
public interface SessionService {

    /**
     * Cierra la sesión del solicitante y deja registrado el evento de auditoría (RF-MS01-04), el cierre no
     * revoca el token ya emitido, que sigue siendo válido hasta que expire (RN-MS01-10)
     *
     * @param requester identidad de quien cierra la sesión
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     */
    void logout(RequesterIdentity requester, String correlationId);
}
