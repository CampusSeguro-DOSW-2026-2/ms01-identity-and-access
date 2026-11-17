package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * The email with which registration is attempted already belongs to another user (RF-MS01-01 alternate flow A1,
 * RN-MS01-01), it is translated to HTTP 409 with code CONFLICTO, the message does not include the email
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public static final String MESSAGE = "El correo ya está registrado.";

        /**
     * Creates the exception with the message of form F-01
     */
    public EmailAlreadyExistsException() {
        super(MESSAGE);
    }
}
