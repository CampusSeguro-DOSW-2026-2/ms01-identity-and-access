package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * The account exists and the credentials are correct, but it is in INACTIVA status
 *
 * RF-MS01-02 (alternate flow A2 and RN-MS01-06)
 *
 * Alternate Flow A2: The system answers 403, indicates that an administrator must be contacted and emits the audit
 * event to MS08
 *
 * RN-MS01-06: An INACTIVA account never logs in, even if the credentials are correct
 */
public class InactiveAccountException extends RuntimeException {

    public static final String MESSAGE = "Cuenta inactiva. Contacte al administrador.";

        /**
     * Creates the exception with the message of form F-02, without a stack trace because it is an expected business
     * result
     */
    public InactiveAccountException() {
        super(MESSAGE, null, false, false);
    }
}
