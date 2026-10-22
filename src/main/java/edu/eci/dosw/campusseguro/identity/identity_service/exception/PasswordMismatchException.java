package edu.eci.dosw.campusseguro.identity.identity_service.exception;

public class PasswordMismatchException extends RuntimeException {

    public PasswordMismatchException() {
        super("Las contraseñas no coinciden.");
    }
}