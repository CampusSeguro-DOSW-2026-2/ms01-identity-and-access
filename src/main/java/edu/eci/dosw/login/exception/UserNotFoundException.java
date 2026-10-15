package edu.eci.dosw.login.exception;

public class UserNotFoundException extends RuntimeException{
    public static final String NOT_FOUND = "NO SE ENCONTRO EL RECURSO CON LA ID PROPUESTA ";

    public UserNotFoundException(Long id){
        super(NOT_FOUND + id);
    }
}
