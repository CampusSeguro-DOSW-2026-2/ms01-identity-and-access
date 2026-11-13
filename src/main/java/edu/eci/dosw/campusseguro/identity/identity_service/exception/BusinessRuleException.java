package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * La petición es válida pero incumple una regla de negocio, por ejemplo asignar ms01:role:manage a un rol
 * distinto de ADMIN, se traduce a HTTP 422 con código REGLA_NEGOCIO
 */
public class BusinessRuleException extends RuntimeException {

    /**
     * Crea la excepción con la regla que se incumple
     *
     * @param message regla incumplida que se devuelve al cliente
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
