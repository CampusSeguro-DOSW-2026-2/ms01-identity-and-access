package edu.eci.dosw.login.exception;

/**
 * Usuario inexistente. El mensaje es claro para el frontend y no expone
 * informacion interna.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("No se encontró un usuario con id " + id);
    }

    public UserNotFoundException(String email) {
        super("No se encontró un usuario con el correo " + email);
    }
}