package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * Credenciales incorrectas o cuenta inexistente (RF-MS01-02, flujo alterno A1)
 *
 * By RN-MS01-05 the same exception is used, with the same message, both when the email does not exist and when the
 * password does not match, so that the client cannot distinguish them.
 * It does not record a stack trace: it is an expected business result, not a system error
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "Credenciales incorrectas.";

        /**
     * Creates the exception with the generic message of form F-02, without a stack trace
     */
    public InvalidCredentialsException() {
        super(MESSAGE, null, false, false);
    }
}
