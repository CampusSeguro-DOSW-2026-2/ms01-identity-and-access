package edu.eci.dosw.campusseguro.identity.identity_service.exception;

public class InvalidResetCodeException extends RuntimeException {

    public InvalidResetCodeException() {
        super("Código incorrecto o vencido.");
    }
}