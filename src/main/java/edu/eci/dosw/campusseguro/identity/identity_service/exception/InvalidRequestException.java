package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.List;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;

/**
 * La peticion tiene datos invalidos o rompe una regla de validacion (RG-05, RG-06). Se traduce a HTTP
 * 400 con codigo VALIDACION, el mensaje indicado y el detalle por campo cuando lo hay.
 */
public class InvalidRequestException extends RuntimeException {

    private final List<ErrorResponse.ErrorDetail> errors;

    /**
     * Crea la excepcion con el mensaje general y el detalle por campo.
     *
     * @param message mensaje que se devolvera al cliente en el campo mensaje
     * @param errors errores por campo; vacia si el mensaje no corresponde a un campo concreto
     */
    public InvalidRequestException(String message, List<ErrorResponse.ErrorDetail> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    /**
     * Devuelve el detalle de los errores por campo.
     *
     * @return lista inmutable de errores por campo; vacia si no hay detalle
     */
    public List<ErrorResponse.ErrorDetail> getErrors() {
        return errors;
    }
}
