package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * The requester cannot perform the operation: it does not have the necessary permission or it tries to change its own
 * account
 */
public class ForbiddenOperationException extends RuntimeException {

        /**
     * Creates the exception with the reason why the operation is forbidden
     *
     * @param message reason why the operation is forbidden
     */
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
