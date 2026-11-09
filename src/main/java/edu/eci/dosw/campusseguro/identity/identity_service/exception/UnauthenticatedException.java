package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * No se pudo establecer la identidad de quien hace la peticion: faltan las cabeceras X-User-Id /
 * X-User-Role o su valor no es valido (RN-MS01-23). Se traduce a HTTP 401 con codigo NO_AUTENTICADO
 */
public class UnauthenticatedException extends RuntimeException {

    /**
     * Crea la excepcion con el motivo que se devolvera al cliente
     *
     * @param message motivo por el que no se pudo establecer la identidad
     */
    public UnauthenticatedException(String message) {
        super(message);
    }
}
