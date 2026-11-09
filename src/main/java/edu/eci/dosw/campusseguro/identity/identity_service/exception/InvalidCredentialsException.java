package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * Credenciales incorrectas o cuenta inexistente (RF-MS01-02, flujo alterno A1)
 *
 * Por RN-MS01-05 se usa la misma excepcion, con el mismo mensaje, tanto cuando el correo no existe
 * como cuando la contrasena no coincide, de modo que el cliente no pueda distinguirlos.
 * No registra traza de pila: es un resultado esperado del negocio, no un error del sistema
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "Credenciales incorrectas.";

    /**
     * Crea la excepción con el mensaje genérico del formulario F-02, sin traza de pila
     */
    public InvalidCredentialsException() {
        super(MESSAGE, null, false, false);
    }
}
