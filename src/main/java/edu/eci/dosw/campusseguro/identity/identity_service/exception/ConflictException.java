package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * La operación choca con el estado actual de un recurso, por ejemplo un nombre de rol repetido o un rol de
 * sistema que no se puede renombrar ni eliminar, se traduce a HTTP 409 con código CONFLICTO
 */
public class ConflictException extends RuntimeException {

    /**
     * Crea la excepción con el motivo del conflicto
     *
     * @param message motivo que se devuelve al cliente
     */
    public ConflictException(String message) {
        super(message);
    }
}
