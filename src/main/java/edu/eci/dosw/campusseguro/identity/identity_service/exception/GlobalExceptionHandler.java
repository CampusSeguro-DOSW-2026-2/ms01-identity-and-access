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
 * Manejador global de excepciones (RNF-MS01-04, Estándares de Integración, sección 7.3), todo error,
 * incluidos los del framework, sale con el ErrorResponse de cinco campos y un código del catálogo, sin
 * exponer trazas internas
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String REQUIRED_PARAMETER_MESSAGE = "El parámetro es obligatorio.";
    public static final String INVALID_PARAMETER_MESSAGE = "Valor no válido.";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * RF-MS01-02 A1: credenciales incorrectas o cuenta inexistente, el sistema responde 401 con un
     * mensaje genérico y no revela cuál de los dos datos falló (RN-MS01-05)
     *
     * @param ex excepción con el mensaje genérico
     * @param request petición, de la que se toma el correlation id
     * @return 401 con el cuerpo de error normalizado
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
            HttpServletRequest request) {
        return build(ErrorCode.BAD_CREDENTIALS, ex.getMessage(), List.of(), request);
    }

    /**
     * RF-MS01-02 A2: cuenta inactiva, el sistema responde 403 e indica que debe contactar a un
     * administrador (RN-MS01-06)
     *
     * @param ex excepción con el mensaje del formulario F-02
     * @param request petición, de la que se toma el correlation id
     * @return 403 con el cuerpo de error normalizado
     */
    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ErrorResponse> handleInactiveAccount(InactiveAccountException ex,
            HttpServletRequest request) {
        return build(ErrorCode.INACTIVE_ACCOUNT, ex.getMessage(), List.of(), request);
    }

    /**
     * RN-MS01-23: no se pudo establecer la identidad de quien llama (faltan las cabeceras X-User-Id /
     * X-User-Role o su valor no es válido), el sistema responde 401 con código NO_AUTENTICADO
     *
     * @param ex excepción con el motivo
     * @param request petición, de la que se toma el correlation id
     * @return 401 con el cuerpo de error normalizado
     */
    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(UnauthenticatedException ex,
            HttpServletRequest request) {
        return build(ErrorCode.UNAUTHENTICATED, ex.getMessage(), List.of(), request);
    }

    /**
     * El solicitante no puede realizar la operación porque no tiene el permiso necesario o porque intenta
     * cambiar su propia cuenta (RF-MS01-05 A1 y A2, RF-MS01-07 A1 y A2), el sistema responde 403 con código
     * PROHIBIDO
     *
     * @param ex excepción con el motivo
     * @param request petición, de la que se toma el correlation id
     * @return 403 con el cuerpo de error normalizado
     */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenOperation(ForbiddenOperationException ex,
            HttpServletRequest request) {
        return build(ErrorCode.FORBIDDEN, ex.getMessage(), List.of(), request);
    }

    /**
     * RG-06: datos inválidos o regla de validación incumplida, el sistema responde 400 con código
     * VALIDACION y el detalle por campo
     *
     * @param ex excepción con el mensaje y los errores por campo
     * @param request petición, de la que se toma el correlation id
     * @return 400 con el cuerpo de error normalizado
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex,
            HttpServletRequest request) {
        return build(ErrorCode.VALIDATION, ex.getMessage(), ex.getErrors(), request);
    }

    /**
     * RG-06: el correo ya está registrado, el sistema responde 409 con código CONFLICTO
     *
     * @param ex excepción con el mensaje del formulario F-01
     * @param request petición, de la que se toma el correlation id
     * @return 409 con el cuerpo de error normalizado
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex,
            HttpServletRequest request) {
        return build(ErrorCode.CONFLICT, ex.getMessage(), List.of(), request);
    }

    /**
     * Uno o más usuarios de la petición no existen, el sistema responde 404 con código NO_ENCONTRADO y no
     * aplica ningún cambio
     *
     * @param ex excepción con el mensaje del formulario F-08 y F-09
     * @param request petición, de la que se toma el correlation id
     * @return 404 con el cuerpo de error normalizado
     */
    @ExceptionHandler({ UserNotFoundException.class, UsersNotFoundException.class })
    public ResponseEntity<ErrorResponse> handleUserNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, ex.getMessage(), List.of(), request);
    }

    /**
     * Falla la validación declarativa de un cuerpo, el sistema responde 400 con código VALIDACION y un
     * error por campo con el nombre que tiene en el contrato
     *
     * @param ex excepción con los errores de validación
     * @param request petición, de la que se toma el correlation id
     * @return 400 con el cuerpo de error normalizado
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
     * El cuerpo no se puede leer (JSON mal formado, tipos incorrectos o cuerpo ausente), el sistema
     * responde 400 con código VALIDACION, el detalle de la causa no se devuelve ni se registra porque
     * puede contener datos del cliente
     *
     * @param ex excepción de lectura del cuerpo
     * @param request petición, de la que se toma el correlation id
     * @return 400 con el cuerpo de error normalizado
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Cuerpo de la petición ilegible: {}", ex.getClass().getSimpleName());
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, List.of(), request);
    }

    /**
     * Falta una cabecera obligatoria, si es una de identidad el sistema responde 401 NO_AUTENTICADO
     * (RN-MS01-23) y si es otra responde 400 VALIDACION
     *
     * @param ex excepción con el nombre de la cabecera ausente
     * @param request petición, de la que se toma el correlation id
     * @return 401 o 400 con el cuerpo de error normalizado
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
            HttpServletRequest request) {
        String header = ex.getHeaderName();
        if (IdentityHeaderParser.USER_ID_HEADER.equalsIgnoreCase(header)
                || IdentityHeaderParser.USER_ROLE_HEADER.equalsIgnoreCase(header)) {
            return build(ErrorCode.UNAUTHENTICATED, IdentityHeaderParser.MISSING_MESSAGE, List.of(), request);
        }
        List<ErrorResponse.ErrorDetail> errors = List.of(
                new ErrorResponse.ErrorDetail(header, REQUIRED_PARAMETER_MESSAGE));
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, errors, request);
    }

    /**
     * Falta un parámetro de consulta obligatorio, el sistema responde 400 con código VALIDACION
     *
     * @param ex excepción con el nombre del parámetro ausente
     * @param request petición, de la que se toma el correlation id
     * @return 400 con el cuerpo de error normalizado
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        List<ErrorResponse.ErrorDetail> errors = List.of(
                new ErrorResponse.ErrorDetail(ex.getParameterName(), REQUIRED_PARAMETER_MESSAGE));
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, errors, request);
    }

    /**
     * Un parámetro de ruta o de consulta tiene un tipo inválido, el sistema responde 400 con código
     * VALIDACION
     *
     * @param ex excepción con el nombre del parámetro
     * @param request petición, de la que se toma el correlation id
     * @return 400 con el cuerpo de error normalizado
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        List<ErrorResponse.ErrorDetail> errors = List.of(
                new ErrorResponse.ErrorDetail(ex.getName(), INVALID_PARAMETER_MESSAGE));
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, errors, request);
    }

    /**
     * La ruta existe pero no admite el método HTTP usado, el sistema responde 405 con código
     * METODO_NO_PERMITIDO
     *
     * @param ex excepción con los métodos que sí admite la ruta
     * @param request petición, de la que se toma el correlation id
     * @return 405 con el cuerpo de error normalizado
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage(), List.of(), request,
                ex.getHeaders());
    }

    /**
     * El cuerpo llega con un tipo de contenido que la ruta no admite, el sistema responde 415 con código
     * TIPO_NO_SOPORTADO
     *
     * @param ex excepción con los tipos de contenido que sí admite la ruta
     * @param request petición, de la que se toma el correlation id
     * @return 415 con el cuerpo de error normalizado
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), List.of(),
                request, ex.getHeaders());
    }

    /**
     * La ruta pedida no existe, el sistema responde 404 con código NO_ENCONTRADO
     *
     * @param ex excepción de ruta inexistente
     * @param request petición, de la que se toma el correlation id
     * @return 404 con el cuerpo de error normalizado
     */
    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ErrorResponse> handleRouteNotFound(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage(), List.of(), request);
    }

    /**
     * Cualquier otra excepción: las propias de Spring MVC que ya traen su estado HTTP se traducen al
     * código del catálogo que corresponde a ese estado, y una falla técnica no controlada responde 500 con
     * código ERROR_INTERNO sin exponer trazas, el detalle queda solo en el log con el correlation id
     *
     * @param ex excepción no controlada
     * @param request petición, de la que se toma el correlation id
     * @return la respuesta con el cuerpo de error normalizado
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            return fromFrameworkStatus(springError, request);
        }
        log.error("Error no controlado", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), List.of(), request);
    }

    /**
     * Traduce una excepción del framework al código del catálogo que corresponde a su estado HTTP
     *
     * @param springError excepción de Spring MVC que ya trae su estado HTTP
     * @param request petición, de la que se toma el correlation id
     * @return la respuesta con el cuerpo de error normalizado
     */
    private ResponseEntity<ErrorResponse> fromFrameworkStatus(org.springframework.web.ErrorResponse springError,
            HttpServletRequest request) {
        int value = springError.getStatusCode().value();
        if (value == HttpStatus.NOT_ACCEPTABLE.value()) {
            value = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value();
        }
        if (value >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Error del framework con estado {}", value, (Exception) springError);
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
     * Construye la respuesta de error sin cabeceras adicionales, con el estado HTTP del código
     *
     * @param code código del catálogo de estados y códigos
     * @param message texto seguro en español
     * @param errors errores por campo, vacío si no hay
     * @param request petición, de la que se toma el correlation id
     * @return la respuesta con el cuerpo de error normalizado
     */
    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message,
            List<ErrorResponse.ErrorDetail> errors, HttpServletRequest request) {
        return build(code, message, errors, request, HttpHeaders.EMPTY);
    }

    /**
     * Construye la respuesta de error con el estado HTTP del código y las cabeceras que exige el protocolo
     * para ese estado, como Allow en un 405
     *
     * @param code código del catálogo de estados y códigos
     * @param message texto seguro en español
     * @param errors errores por campo, vacío si no hay
     * @param request petición, de la que se toma el correlation id
     * @param headers cabeceras adicionales de la respuesta
     * @return la respuesta con el cuerpo de error normalizado
     */
    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message,
            List<ErrorResponse.ErrorDetail> errors, HttpServletRequest request, HttpHeaders headers) {
        return respond(HttpStatus.valueOf(code.getStatus()), code, message, errors, request, headers);
    }

    /**
     * Construye la respuesta de error con el cuerpo en JSON, se usa cuando el estado HTTP de la respuesta
     * no es el del código porque lo fija el framework
     *
     * @param status estado HTTP de la respuesta
     * @param code código del catálogo de estados y códigos
     * @param message texto seguro en español
     * @param errors errores por campo, vacío si no hay
     * @param request petición, de la que se toma el correlation id
     * @param headers cabeceras adicionales de la respuesta
     * @return la respuesta con el cuerpo de error normalizado
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
