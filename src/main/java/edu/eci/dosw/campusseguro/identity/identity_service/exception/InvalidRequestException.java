package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.List;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;

/**
 * The request has invalid data or breaks a validation rule (RG-05, RG-06). It is translated to HTTP 400 with code
 * VALIDACION, the indicated message and the detail by field when there is one
 */
public class InvalidRequestException extends RuntimeException {

    private final List<ErrorResponse.ErrorDetail> errors;

        /**
     * Creates the exception with the general message and the detail by field
     *
     * @param message message that will be returned to the client in the mensaje field
     * @param errors errors by field; empty if the message does not correspond to a specific field
     */
    public InvalidRequestException(String message, List<ErrorResponse.ErrorDetail> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

        /**
     * Returns the detail of the errors by field
     *
     * @return immutable list of errors by field; empty if there is no detail
     */
    public List<ErrorResponse.ErrorDetail> getErrors() {
        return errors;
    }
}
