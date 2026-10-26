package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * El correo con el que se intenta registrar ya pertenece a otro usuario (RF-MS01-01 flujo alterno A1,
 * RN-MS01-01), se traduce a HTTP 409 con código CONFLICTO, el mensaje no incluye el correo
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public static final String MESSAGE = "El correo ya está registrado.";

    /**
     * Crea la excepción con el mensaje del formulario F-01
     */
    public EmailAlreadyExistsException() {
        super(MESSAGE);
    }
}
