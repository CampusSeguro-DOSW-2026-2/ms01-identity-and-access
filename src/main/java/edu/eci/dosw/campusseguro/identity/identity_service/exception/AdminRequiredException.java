package edu.eci.dosw.campusseguro.identity.identity_service.exception;

public class AdminRequiredException extends RuntimeException {
    public AdminRequiredException() {
        super("Se requiere rol ADMIN"); 
    }
}
