package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * Incorrect, expired or already used recovery code (RF-MS01-08, alternate flow A1)
 *
 * The same exception, with the same message, is used for all the causes and also when the email does not correspond to
 * an active account, so that the client cannot distinguish them. It does not record a stack trace: it is an expected
 * business result, not a system error
 */
public class InvalidResetCodeException extends RuntimeException {

    public static final String MESSAGE = "Código incorrecto o vencido.";

        /**
     * Creates the exception with the generic message of form F-05, without a stack trace
     */
    public InvalidResetCodeException() {
        super(MESSAGE, null, false, false);
    }
}
