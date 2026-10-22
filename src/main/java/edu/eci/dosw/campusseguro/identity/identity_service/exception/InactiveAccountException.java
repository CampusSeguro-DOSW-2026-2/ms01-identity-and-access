package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * La cuenta existe y las credenciales son correctas, pero esta INACTIVA
 * (RF-MS01-02, flujo alterno A2 y RN-MS01-06).
 */
public class InactiveAccountException extends RuntimeException {

    public static final String MESSAGE = "Cuenta inactiva. Contacte al administrador.";

    public InactiveAccountException() {
        super(MESSAGE, null, false, false);
    }
}
