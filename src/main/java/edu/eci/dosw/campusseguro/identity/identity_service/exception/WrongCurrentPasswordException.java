package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * RF-MS01-08 A3: la contrasena actual enviada no coincide con la almacenada.
 */
public class WrongCurrentPasswordException extends RuntimeException {

    public WrongCurrentPasswordException() {
        super("La contraseña actual es incorrecta.");
    }
}