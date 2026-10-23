package edu.eci.dosw.campusseguro.identity.identity_service.exception;

public class AdminOrAuditorRequiredException extends RuntimeException {
    public AdminOrAuditorRequiredException() {
        super("Se requiere rol ADMIN"); 
    }
}
