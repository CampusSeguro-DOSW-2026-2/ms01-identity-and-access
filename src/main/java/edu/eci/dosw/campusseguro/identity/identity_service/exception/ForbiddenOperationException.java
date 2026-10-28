package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * El solicitante no puede realizar la operacion: no tiene el permiso necesario o intenta cambiar su
 * propia cuenta
 */
public class ForbiddenOperationException extends RuntimeException {

    /**
     * Crea la excepcion con el motivo por el que se prohibe la operacion
     *
     * @param message motivo por el que se prohibe la operacion
     */
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
