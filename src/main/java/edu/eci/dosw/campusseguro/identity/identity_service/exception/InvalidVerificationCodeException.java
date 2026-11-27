package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * Incorrect or expired verification code of the registration (RF-MS01-01)
 *
 * The same exception, with the same message, is used for all the causes and also when the email has no pending
 * registration, so that the client cannot distinguish them. It does not record a stack trace: it is an expected
 * business result, not a system error
 */
public class InvalidVerificationCodeException extends RuntimeException {

    public static final String MESSAGE = "Código incorrecto o vencido.";

        /**
     * Creates the exception with the generic message of form F-14, without a stack trace
     */
    public InvalidVerificationCodeException() {
        super(MESSAGE, null, false, false);
    }
}
