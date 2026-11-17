package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Global exception handler (RNF-MS01-04, Integration Standards, section 7.3), every error, including the ones of the
 * framework, leaves with the five-field ErrorResponse and a code of the catalog, without exposing internal traces
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String REQUIRED_PARAMETER_MESSAGE = "El parámetro es obligatorio.";
    public static final String INVALID_PARAMETER_MESSAGE = "Valor no válido.";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        /**
     * RF-MS01-02 A1: incorrect credentials or non-existent account, the system answers 401 with a generic message and
     * does not reveal which of the two data failed (RN-MS01-05)
     *
     * @param ex exception with the generic message
     * @param request request, from which the correlation id is taken
     * @return 401 with the normalized error body
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
            HttpServletRequest request) {
        return build(ErrorCode.BAD_CREDENTIALS, ex.getMessage(), List.of(), request);
    }

        /**
     * RF-MS01-02 A2: inactive account, the system answers 403 and indicates that an administrator must be contacted
     * (RN-MS01-06)
     *
     * @param ex exception with the message of form F-02
     * @param request request, from which the correlation id is taken
     * @return 403 with the normalized error body
     */
    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ErrorResponse> handleInactiveAccount(InactiveAccountException ex,
            HttpServletRequest request) {
        return build(ErrorCode.INACTIVE_ACCOUNT, ex.getMessage(), List.of(), request);
    }

        /**
     * RF-MS01-08 A1: incorrect, expired or already used recovery code, the system answers 400 with code CODIGO_INVALIDO
     * and a generic message that does not reveal the cause
     *
     * @param ex exception with the message of form F-05
     * @param request request, from which the correlation id is taken
     * @return 400 with the normalized error body
     */
    @ExceptionHandler(InvalidResetCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetCode(InvalidResetCodeException ex,
            HttpServletRequest request) {
        return build(ErrorCode.INVALID_CODE, ex.getMessage(), List.of(), request);
    }

        /**
     * RN-MS01-23: the identity of the caller could not be established (the headers X-User-Id / X-User-Role /
     * X-User-Permission are missing or their value is not valid), the system answers 401 with code NO_AUTENTICADO
     *
     * @param ex exception with the reason
     * @param request request, from which the correlation id is taken
     * @return 401 with the normalized error body
     */
    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(UnauthenticatedException ex,
            HttpServletRequest request) {
        return build(ErrorCode.UNAUTHENTICATED, ex.getMessage(), List.of(), request);
    }

        /**
     * The requester cannot perform the operation because it does not have the necessary permission or because it tries
     * to change its own account (RF-MS01-05 A1 and A2, RF-MS01-07 A1 and A2), the system answers 403 with code
     * PROHIBIDO
     *
     * @param ex exception with the reason
     * @param request request, from which the correlation id is taken
     * @return 403 with the normalized error body
     */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenOperation(ForbiddenOperationException ex,
            HttpServletRequest request) {
        return build(ErrorCode.FORBIDDEN, ex.getMessage(), List.of(), request);
    }

        /**
     * RG-06: invalid data or a broken validation rule, the system answers 400 with code VALIDACION and the detail by
     * field
     *
     * @param ex exception with the message and the errors by field
     * @param request request, from which the correlation id is taken
     * @return 400 with the normalized error body
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex,
            HttpServletRequest request) {
        return build(ErrorCode.VALIDATION, ex.getMessage(), ex.getErrors(), request);
    }

        /**
     * RG-06: the email is already registered, the system answers 409 with code CONFLICTO
     *
     * @param ex exception with the message of form F-01
     * @param request request, from which the correlation id is taken
     * @return 409 with the normalized error body
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex,
            HttpServletRequest request) {
        return build(ErrorCode.CONFLICT, ex.getMessage(), List.of(), request);
    }

        /**
     * RF-MS01-10: the role name already exists or the role is a system role and does not allow the operation, the
     * system answers 409 with code CONFLICTO
     *
     * @param ex exception with the message of form F-10, F-11 or F-13
     * @param request request, from which the correlation id is taken
     * @return 409 with the normalized error body
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(ErrorCode.CONFLICT, ex.getMessage(), List.of(), request);
    }

        /**
     * RN-MS01-30: the request is valid but breaks a business rule, the system answers 422 with code REGLA_NEGOCIO
     *
     * @param ex exception with the message of form F-10 or F-12
     * @param request request, from which the correlation id is taken
     * @return 422 with the normalized error body
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return build(ErrorCode.BUSINESS_RULE, ex.getMessage(), List.of(), request);
    }

        /**
     * One or more users or the role of the request do not exist, the system answers 404 with code NO_ENCONTRADO and
     * applies no change
     *
     * @param ex exception with the message of the forms F-08, F-09, F-11, F-12 and F-13
     * @param request request, from which the correlation id is taken
     * @return 404 with the normalized error body
     */
    @ExceptionHandler({ UserNotFoundException.class, UsersNotFoundException.class, RoleNotFoundException.class })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, ex.getMessage(), List.of(), request);
    }

        /**
     * The declarative validation of a body fails, the system answers 400 with code VALIDACION and one error by field
     * with the name it has in the contract
     *
     * @param ex exception with the validation errors
     * @param request request, from which the correlation id is taken
     * @return 400 with the normalized error body
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Object target = ex.getBindingResult().getTarget();
        Class<?> type = Object.class;
        if (target != null) {
            type = target.getClass();
        }
        List<ErrorResponse.ErrorDetail> errors = ValidationErrors.fromFieldErrors(type,
                ex.getBindingResult().getFieldErrors());
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, errors, request);
    }

        /**
     * The body cannot be read (malformed JSON, wrong types or missing body), the system answers 400 with code
     * VALIDACION, the detail of the cause is neither returned nor logged because it may contain client data
     *
     * @param ex body reading exception
     * @param request request, from which the correlation id is taken
     * @return 400 with the normalized error body
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Unreadable request body: {}", ex.getClass().getSimpleName());
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, List.of(), request);
    }

        /**
     * A required header is missing, if it is an identity one the system answers 401 NO_AUTENTICADO (RN-MS01-23) and if
     * it is another one it answers 400 VALIDACION
     *
     * @param ex exception with the name of the missing header
     * @param request request, from which the correlation id is taken
     * @return 401 or 400 with the normalized error body
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
            HttpServletRequest request) {
        String header = ex.getHeaderName();
        if (IdentityHeaderParser.USER_ID_HEADER.equalsIgnoreCase(header)
                || IdentityHeaderParser.USER_ROLE_HEADER.equalsIgnoreCase(header)
                || IdentityHeaderParser.USER_PERMISSION_HEADER.equalsIgnoreCase(header)) {
            return build(ErrorCode.UNAUTHENTICATED, IdentityHeaderParser.MISSING_MESSAGE, List.of(), request);
        }
        List<ErrorResponse.ErrorDetail> errors = List.of(
                new ErrorResponse.ErrorDetail(header, REQUIRED_PARAMETER_MESSAGE));
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, errors, request);
    }

        /**
     * A required query parameter is missing, the system answers 400 with code VALIDACION
     *
     * @param ex exception with the name of the missing parameter
     * @param request request, from which the correlation id is taken
     * @return 400 with the normalized error body
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        List<ErrorResponse.ErrorDetail> errors = List.of(
                new ErrorResponse.ErrorDetail(ex.getParameterName(), REQUIRED_PARAMETER_MESSAGE));
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, errors, request);
    }

        /**
     * A path or query parameter has an invalid type, the system answers 400 with code VALIDACION
     *
     * @param ex exception with the name of the parameter
     * @param request request, from which the correlation id is taken
     * @return 400 with the normalized error body
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        List<ErrorResponse.ErrorDetail> errors = List.of(
                new ErrorResponse.ErrorDetail(ex.getName(), INVALID_PARAMETER_MESSAGE));
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, errors, request);
    }

        /**
     * The route exists but does not allow the HTTP method used, the system answers 405 with code METODO_NO_PERMITIDO
     *
     * @param ex exception with the methods that the route does allow
     * @param request request, from which the correlation id is taken
     * @return 405 with the normalized error body
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage(), List.of(), request,
                ex.getHeaders());
    }

        /**
     * The body arrives with a content type that the route does not accept, the system answers 415 with code
     * TIPO_NO_SOPORTADO
     *
     * @param ex exception with the content types that the route does accept
     * @param request request, from which the correlation id is taken
     * @return 415 with the normalized error body
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), List.of(),
                request, ex.getHeaders());
    }

        /**
     * The requested route does not exist, the system answers 404 with code NO_ENCONTRADO
     *
     * @param ex non-existent route exception
     * @param request request, from which the correlation id is taken
     * @return 404 with the normalized error body
     */
    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ErrorResponse> handleRouteNotFound(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage(), List.of(), request);
    }

        /**
     * Any other exception: the Spring MVC ones that already carry their HTTP status are translated to the catalog code
     * that corresponds to that status, and an uncontrolled technical failure answers 500 with code ERROR_INTERNO
     * without exposing traces, the detail stays only in the log with the correlation id
     *
     * @param ex uncontrolled exception
     * @param request request, from which the correlation id is taken
     * @return the response with the normalized error body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            return fromFrameworkStatus(springError, request);
        }
        log.error("Uncontrolled error", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), List.of(), request);
    }

        /**
     * Translates a framework exception to the catalog code that corresponds to its HTTP status
     *
     * @param springError Spring MVC exception that already carries its HTTP status
     * @param request request, from which the correlation id is taken
     * @return the response with the normalized error body
     */
    private ResponseEntity<ErrorResponse> fromFrameworkStatus(org.springframework.web.ErrorResponse springError,
            HttpServletRequest request) {
        int value = springError.getStatusCode().value();
        if (value == HttpStatus.NOT_ACCEPTABLE.value()) {
            value = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value();
        }
        if (value >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Framework error with status {}", value, (Exception) springError);
            value = HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        HttpStatus status = HttpStatus.resolve(value);
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        ErrorCode code = ErrorCode.forStatus(status.value());
        return respond(status, code, code.getMessage(), List.of(), request, springError.getHeaders());
    }

        /**
     * Builds the error response without additional headers, with the HTTP status of the code
     *
     * @param code code of the catalog of statuses and codes
     * @param message safe text in Spanish
     * @param errors errors by field, empty if there are none
     * @param request request, from which the correlation id is taken
     * @return the response with the normalized error body
     */
    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message,
            List<ErrorResponse.ErrorDetail> errors, HttpServletRequest request) {
        return build(code, message, errors, request, HttpHeaders.EMPTY);
    }

        /**
     * Builds the error response with the HTTP status of the code and the headers that the protocol requires for that
     * status, such as Allow in a 405
     *
     * @param code code of the catalog of statuses and codes
     * @param message safe text in Spanish
     * @param errors errors by field, empty if there are none
     * @param request request, from which the correlation id is taken
     * @param headers additional headers of the response
     * @return the response with the normalized error body
     */
    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message,
            List<ErrorResponse.ErrorDetail> errors, HttpServletRequest request, HttpHeaders headers) {
        return respond(HttpStatus.valueOf(code.getStatus()), code, message, errors, request, headers);
    }

        /**
     * Builds the error response with the body in JSON, it is used when the HTTP status of the response is not the one
     * of the code because the framework sets it
     *
     * @param status HTTP status of the response
     * @param code code of the catalog of statuses and codes
     * @param message safe text in Spanish
     * @param errors errors by field, empty if there are none
     * @param request request, from which the correlation id is taken
     * @param headers additional headers of the response
     * @return the response with the normalized error body
     */
    private ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorCode code, String message,
            List<ErrorResponse.ErrorDetail> errors, HttpServletRequest request, HttpHeaders headers) {
        ErrorResponse body = new ErrorResponse(status.value(), code.getCode(), message, errors,
                CorrelationIds.current(request));
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
