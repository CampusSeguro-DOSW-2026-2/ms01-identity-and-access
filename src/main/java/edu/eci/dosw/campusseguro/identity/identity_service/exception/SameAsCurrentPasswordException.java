package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * F-06: la nueva contrasena es igual a la actual.
 */
public class SameAsCurrentPasswordException extends RuntimeException {

    public SameAsCurrentPasswordException() {
        super("La nueva contraseña no cumple la política o es igual a la actual.");
    }
}