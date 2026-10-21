package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException{

    public static final String NOT_FOUND = "NO SE ENCONTRO EL RECURSO CON LA ID PROPUESTA ";

    public UserNotFoundException(UUID id){
        super(NOT_FOUND + id);
    }
    
}
