package edu.eci.dosw.campusseguro.identity.identity_service.audit;

/**
 * Acciones auditables de MS01 (RNF-MS01-10)
 */
public enum AuditAction {
    REGISTRO,
    LOGIN,
    LOGOUT,
    CAMBIO_ROL,
    CREACION_ROL,
    EDICION_ROL,
    CAMBIO_PERMISOS_ROL,
    ELIMINACION_ROL,
    CAMBIO_ESTADO,
    CAMBIO_CONTRASENA,
    RECUPERACION
}
