package edu.eci.dosw.campusseguro.identity.identity_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * RF-MS01-04 Cerrar sesión (logout)
 *
 * Flujo base: identificar al usuario autenticado (X-User-Id) -> auditar LOGOUT/EXITO -> responder
 *
 * RN-MS01-10: El cierre de sesión queda auditado y no revoca el token ya emitido, que sigue siendo válido
 * hasta que expire, el cliente lo descarta
 *
 * RNF-MS01-10: Se audita el cierre de sesión exitoso y el rechazado por falta de permiso, el fallo al
 * auditar nunca cambia la respuesta
 */
@Service
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    static final String REASON_NOT_ALLOWED = "SIN_PERMISO";

    private final AuditRecorder auditRecorder;

    /**
     * Crea el servicio con su dependencia
     *
     * @param auditRecorder registrador de los eventos de auditoría de MS08
     */
    public SessionServiceImpl(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    /**
     * Cierra la sesión del solicitante y deja registrado el evento de auditoría (RF-MS01-04), el cierre no
     * revoca el token ya emitido, que sigue siendo válido hasta que expire (RN-MS01-10)
     *
     * @param requester identidad de quien cierra la sesión
     * @param correlationId valor de X-Correlation-ID, si falta se genera uno para logs y auditoría
     * @throws ForbiddenOperationException si el solicitante no tiene el permiso requerido
     */
    @Override
    public void logout(RequesterIdentity requester, String correlationId) {
        String corr = CorrelationIds.resolve(correlationId);
        String resource = AuditRecorder.userResource(requester.userId());

        if (!requester.hasPermission(Permission.MS01_SESSION_LOGOUT)) {
            auditRecorder.recordAs(AuditAction.LOGOUT, AuditResult.FALLO, requester, resource,
                    REASON_NOT_ALLOWED, corr);
            log.warn("Cierre de sesión rechazado: el solicitante {} no tiene el permiso requerido",
                    requester.userId());
            throw new ForbiddenOperationException(ErrorCode.FORBIDDEN.getMessage());
        }

        auditRecorder.recordAs(AuditAction.LOGOUT, AuditResult.EXITO, requester, resource, null, corr);
        log.info("Cierre de sesión. usuario={}", requester.userId());
    }
}
