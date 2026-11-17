package edu.eci.dosw.campusseguro.identity.identity_service.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.StrictRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Validates the bodies of the requests in the service layer (RNF-MS01-09), so that the authorization is checked first
 * and then the data, and so that the flows that audit the rejections can record them before answering
 */
@Component
public class RequestValidator {

    private final Validator validator;

        /**
     * Creates the validator with the Bean Validation engine
     *
     * @param validator Bean Validation engine
     */
    public RequestValidator(Validator validator) {
        this.validator = validator;
    }

        /**
     * Validates the request and returns all the errors by field found, including the fields that the contract does not
     * define (RG-04)
     *
     * @param request body received
     * @return errors by field, one per field and sorted by name, empty if the request is valid
     */
    public List<ErrorResponse.ErrorDetail> collectErrors(Object request) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();
        if (request instanceof StrictRequest strict) {
            errors.addAll(strict.unknownFieldErrors());
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        errors.addAll(ValidationErrors.fromViolations(request.getClass(), violations));
        return ValidationErrors.merge(errors);
    }

        /**
     * Validates the request and rejects it with HTTP 400 if it has errors
     *
     * @param request body received
     * @throws InvalidRequestException if the request has invalid, missing or undefined fields
     */
    public void validate(Object request) {
        List<ErrorResponse.ErrorDetail> errors = collectErrors(request);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, errors);
        }
    }
}
