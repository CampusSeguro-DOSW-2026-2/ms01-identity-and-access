package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * The identity of whoever makes the request could not be established: the headers X-User-Id / X-User-Role are missing
 * or their value is not valid (RN-MS01-23). It is translated to HTTP 401 with code NO_AUTENTICADO
 */
public class UnauthenticatedException extends RuntimeException {

        /**
     * Creates the exception with the reason that will be returned to the client
     *
     * @param message reason why the identity could not be established
     */
    public UnauthenticatedException(String message) {
        super(message);
    }
}
