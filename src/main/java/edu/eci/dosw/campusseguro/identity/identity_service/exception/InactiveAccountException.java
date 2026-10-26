package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * La cuenta existe y las credenciales son correctas, pero esta en estado INACTIVA
 *
 * RF-MS01-02 (flujo alterno A2 y RN-MS01-06)
 *
 * Flujo Alterno A2: El sistema responde 403, indica que debe contactar a un administrador
 * y emite a MS08 el evento de auditoría
 *
 * RN-MS01-06: Una cuenta INACTIVA nunca inicia sesión, aunque las credenciales sean correctas
 */
public class InactiveAccountException extends RuntimeException {

    public static final String MESSAGE = "Cuenta inactiva. Contacte al administrador.";

    /**
     * Crea la excepción con el mensaje del formulario F-02, sin traza de pila porque es un resultado
     * esperado del negocio
     */
    public InactiveAccountException() {
        super(MESSAGE, null, false, false);
    }
}
