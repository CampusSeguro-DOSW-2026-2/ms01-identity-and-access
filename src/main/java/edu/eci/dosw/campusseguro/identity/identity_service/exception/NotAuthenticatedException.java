package edu.eci.dosw.campusseguro.identity.identity_service.exception;

/**
 * RN-MS01-23: faltan (o son invalidas) las cabeceras de identidad X-User-Id / X-User-Role que
 * inyecta el API Gateway. Se responde 401 NO_AUTENTICADO.
 */
public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException() {
        super("No autenticado.");
    }
}
