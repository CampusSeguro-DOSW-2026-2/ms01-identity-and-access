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
 * Valida los cuerpos de las peticiones en la capa de servicio (RNF-MS01-09), de modo que primero se
 * comprueba la autorización y después los datos, y que los flujos que auditan los rechazos puedan
 * registrarlos antes de responder
 */
@Component
public class RequestValidator {

    private final Validator validator;

    /**
     * Crea el validador con el motor de Bean Validation
     *
     * @param validator motor de Bean Validation
     */
    public RequestValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * Valida la petición y devuelve todos los errores por campo encontrados, incluidos los campos que el
     * contrato no define (RG-04)
     *
     * @param request cuerpo recibido
     * @return errores por campo, uno por campo y ordenados por nombre, vacío si la petición es válida
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
     * Valida la petición y la rechaza con HTTP 400 si tiene errores
     *
     * @param request cuerpo recibido
     * @throws InvalidRequestException si la petición tiene campos inválidos, faltantes o no definidos
     */
    public void validate(Object request) {
        List<ErrorResponse.ErrorDetail> errors = collectErrors(request);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, errors);
        }
    }
}
