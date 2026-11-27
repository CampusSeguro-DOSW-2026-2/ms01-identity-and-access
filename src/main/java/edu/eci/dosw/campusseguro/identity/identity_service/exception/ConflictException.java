package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * The operation clashes with the current state of a resource, for example a repeated role name or a system role that
 * cannot be renamed or deleted, it is translated to HTTP 409 with code CONFLICTO
 */
public class ConflictException extends RuntimeException {

        /**
     * Creates the exception with the reason for the conflict
     *
     * @param message reason that is returned to the client
     */
    public ConflictException(String message) {
        super(message);
    }
}
