package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * Código de recuperación incorrecto, vencido o ya usado (RF-MS01-08, flujo alterno A1)
 *
 * Se usa la misma excepción, con el mismo mensaje, para todas las causas y también cuando el correo no
 * corresponde a una cuenta activa, de modo que el cliente no pueda distinguirlas. No registra traza de pila:
 * es un resultado esperado del negocio, no un error del sistema
 */
public class InvalidResetCodeException extends RuntimeException {

    public static final String MESSAGE = "Código incorrecto o vencido.";

    /**
     * Crea la excepción con el mensaje genérico del formulario F-05, sin traza de pila
     */
    public InvalidResetCodeException() {
        super(MESSAGE, null, false, false);
    }
}
