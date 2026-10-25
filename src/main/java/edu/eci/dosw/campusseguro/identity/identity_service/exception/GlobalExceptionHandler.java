package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /**
     * RF-MS01-02 A1: credenciales incorrectas o cuenta inexistente; el sistema responde 401 con un
     * mensaje genérico (“credenciales incorrectas”) y emite a MS08 el evento de auditoría del
     * intento fallido.
     * 
     * RN-MS01-05: Ante credenciales inválidas no se revela si falló el correo o la contraseña.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "CREDENCIALES_INCORRECTAS", ex.getMessage(),
                correlationId(request)));
    }

    /**
     * RF-MS01-02 A2: cuenta inactiva; el sistema responde 403, indica que debe contactar a un
     * administrador y emite a MS08 el evento de auditoría.
     * 
     * RN-MS01-06: Una cuenta INACTIVA nunca inicia sesión, aunque las credenciales sean correctas.
     * 
    */
    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ErrorResponse> handleInactiveAccount(InactiveAccountException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "CUENTA_INACTIVA", ex.getMessage(),
                correlationId(request)));
    }

    /**
     * RN-MS01-23: no se pudo establecer la identidad de quien llama (faltan las cabeceras X-User-Id /
     * X-User-Role o su valor no es valido); el sistema responde 401 con codigo NO_AUTENTICADO
     *
     * @param ex excepcion con el motivo
     * @param request peticion, de la que se toma el X-Correlation-ID
     * @return 401 con el cuerpo de error normalizado
     */
    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(UnauthenticatedException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "NO_AUTENTICADO", ex.getMessage(),
                correlationId(request)));
    }

    /**
     * RF-MS01-05 A1 y A2: el solicitante no es administrador o intenta modificar sus propios roles; el
     * sistema responde 403 con codigo PROHIBIDO
     *
     * @param ex excepcion con el motivo
     * @param request peticion, de la que se toma el X-Correlation-ID
     * @return 403 con el cuerpo de error normalizado
     */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenOperation(ForbiddenOperationException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "PROHIBIDO", ex.getMessage(),
                correlationId(request)));
    }

    /**
     * RG-06 y F-09: datos invalidos o regla de validacion incumplida (por ejemplo, un usuario que
     * quedaria sin roles); el sistema responde 400 con codigo VALIDACION y el detalle por campo
     *
     * @param ex excepcion con el mensaje y los errores por campo
     * @param request peticion, de la que se toma el X-Correlation-ID
     * @return 400 con el cuerpo de error normalizado
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(), "VALIDACION", ex.getMessage(), ex.getErrors(),
                correlationId(request)));
    }

    /**
     * RF-MS01-05 A3: algun usuario de la peticion no existe; el sistema responde 404 con codigo
     * NO_ENCONTRADO y no aplica ningun cambio
     *
     * @param ex excepcion con el mensaje y los ids que no existen
     * @param request peticion, de la que se toma el X-Correlation-ID
     * @return 404 con el cuerpo de error normalizado
     */
    @ExceptionHandler(UsersNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsersNotFound(UsersNotFoundException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "NO_ENCONTRADO", ex.getMessage(),
                correlationId(request)));
    }

    /**
     * RNF-MS01-04: la ruta existe pero no admite el metodo HTTP usado, el sistema responde 405 con
     * codigo METODO_NO_PERMITIDO
     *
     * @param ex excepcion con los metodos que si admite la ruta
     * @param request peticion, de la que se toma el X-Correlation-ID
     * @return 405 con el cuerpo de error normalizado
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders()).body(
                ErrorResponse.of(HttpStatus.METHOD_NOT_ALLOWED.value(), "METODO_NO_PERMITIDO",
                        "Método HTTP no permitido.", correlationId(request)));
    }

    /**
     * RNF-MS01-04: el cuerpo llega con un tipo de contenido que la ruta no admite, el sistema responde
     * 415 con codigo MEDIO_NO_SOPORTADO
     *
     * @param ex excepcion con los tipos de contenido que si admite la ruta
     * @param request peticion, de la que se toma el X-Correlation-ID
     * @return 415 con el cuerpo de error normalizado
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).headers(ex.getHeaders()).body(
                ErrorResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "MEDIO_NO_SOPORTADO",
                        "Tipo de contenido no soportado.", correlationId(request)));
    }

    /**
     * RNF-MS01-04: la ruta pedida no existe, el sistema responde 404 con codigo NO_ENCONTRADO
     *
     * @param ex excepcion de ruta inexistente
     * @param request peticion, de la que se toma el X-Correlation-ID
     * @return 404 con el cuerpo de error normalizado
     */
    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ErrorResponse> handleRouteNotFound(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "NO_ENCONTRADO", "Recurso no encontrado.",
                correlationId(request)));
    }

    /**
     * RNF-MS01-04: falla tecnica no controlada, el sistema responde 500 con codigo ERROR_INTERNO sin
     * exponer trazas internas, el detalle queda solo en los logs con el correlation id, las excepciones
     * propias de Spring MVC que ya traen su estado se dejan a su tratamiento normal
     *
     * @param ex excepcion no controlada
     * @param request peticion, de la que se toma el X-Correlation-ID
     * @return 500 con el cuerpo de error normalizado
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(RuntimeException ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse) {
            throw ex;
        }
        String correlationId = correlationId(request);
        log.error("[{}] Error no controlado", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "ERROR_INTERNO", "Error interno del servidor.",
                correlationId));
    }

    /**
     * RG-04: Se rechazan (HTTP 400) los campos no definidos en el contrato (en especial roles,
     * estado o id enviados por el usuario en registro y perfil).
    */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDACION", "Datos inválidos.", correlationId(request)));
    }

    /**
     * Atrapa a la excepcion que corresponde al codigo 400 MethodArgumentNotValidException
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        if (ex.getBindingResult().getTarget() instanceof LoginRequest) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(normalizedValidationError(ex, request));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Organiza los mensajes de la excepcion, deja un solo mensaje por campo, y devuelve el error en formato DTO
     * @param ex la excepcion
     * @param request el request que que se envia con el error
     * @return el error en formato DTO (record en este caso)
     */
    private ErrorResponse normalizedValidationError(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> messageByField = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> messageByField.putIfAbsent(e.getField(), e.getDefaultMessage()));
        List<ErrorResponse.ErrorDetail> details = messageByField.entrySet().stream()
                .map(e -> new ErrorResponse.ErrorDetail(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ErrorResponse.ErrorDetail::campo))
                .toList();
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "VALIDACION", "Datos inválidos.", details,
                correlationId(request));
    }

    /**
     * Verifica el estado del correlationId, si es invalido devuelve null, sino elimina espacios a los
     * lados y lo devuelve
     * @param request el request al que se le extrae el correlationId
     * @return el correlactionId verificado, devuelve null si es invalido
     */
    private static String correlationId(HttpServletRequest request) {
        String value = request.getHeader(CORRELATION_HEADER);

        if (value == null || value.isBlank()) {
            return null;
        } else {
            return value.trim();
        }
    }

    /**
     * 
     * @param ex
     * @return
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, String>> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Falta el header " + ex.getHeaderName()));
    }

    /**
     * 
     * @param ex
     * @return
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Valor inválido para " + ex.getName()));
    }

    /**
     * 
     * @param ex
     * @return
     */
    @ExceptionHandler(AdminOrAuditorRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleAdminOrAuditorRequired(AdminOrAuditorRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", 403, "codigo", "ACCESO_DENEGADO",
                "mensaje", "No cuenta con los permisos suficientes para realizar esta operación.", "errores", List.of()));
    }

    /**
     * 
     * @param ex
     * @return
     */
    @ExceptionHandler(SelfStatusChangeForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleSelfStatusChangeForbidden(SelfStatusChangeForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", 403, "codigo", "ACCESO_DENEGADO",
                "mensaje", "Solo otro administrador diferente de usted puede realizar esa accion.", "errores", List.of()));
    }

    /**
     * 
     * @param ex
     * @return
     */
    @ExceptionHandler(InvalidSearchParamException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSearch(InvalidSearchParamException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400, "codigo", "VALIDACION", "mensaje", ex.getMessage(), "errores", List.of()));
    }
}
