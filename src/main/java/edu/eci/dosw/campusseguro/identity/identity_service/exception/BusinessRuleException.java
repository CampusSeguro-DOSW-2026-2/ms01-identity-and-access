package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * The request is valid but breaks a business rule, for example assigning ms01:role:manage to a role other than ADMIN,
 * it is translated to HTTP 422 with code REGLA_NEGOCIO
 */
public class BusinessRuleException extends RuntimeException {

        /**
     * Creates the exception with the rule that is broken
     *
     * @param message rule broken that is returned to the client
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
