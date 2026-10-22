package edu.eci.dosw.campusseguro.identity.identity_service.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    
    public EmailAlreadyExistsException(String email) {
        super("Ya existe un usuario registrado con el correo " + email);
    }
}
