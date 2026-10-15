package edu.eci.dosw.login.exception;

/**
 * Mensaje genérico para evitar user-enumeration (RF-MS01-MVP-02 / RN-MS01-MVP-05).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}