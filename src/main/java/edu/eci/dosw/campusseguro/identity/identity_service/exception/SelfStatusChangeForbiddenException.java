package edu.eci.dosw.campusseguro.identity.identity_service.exception;

public class SelfStatusChangeForbiddenException extends RuntimeException {
    public SelfStatusChangeForbiddenException() {
        super("Un administrador no puede cambiar su propio estado"); 
    }
}
