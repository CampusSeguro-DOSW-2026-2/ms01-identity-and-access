package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
     * RF-MS01-02 A1 / RN-MS01-05: correo inexistente y contrasena incorrecta producen exactamente
     * esta misma respuesta (mismo codigo, mismo mensaje, mismo cuerpo).
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "CREDENCIALES_INCORRECTAS", ex.getMessage(),
                correlationId(request)));
    }

    /** RF-MS01-02 A2 / RN-MS01-06: cuenta INACTIVA. */
    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ErrorResponse> handleInactiveAccount(InactiveAccountException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "CUENTA_INACTIVA", ex.getMessage(),
                correlationId(request)));
    }

    /** Cuerpo ilegible o con campos no definidos en el contrato (RG-04): 400 normalizado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDACION", "Datos inválidos.", correlationId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // El login ya responde con el cuerpo de error normalizado del contrato (RNF-MS01-04, RG-05).
        // El resto de endpoints conserva por ahora su formato original hasta que se unifique el
        // manejo global de errores.
        if (ex.getBindingResult().getTarget() instanceof LoginRequest) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(normalizedValidationError(ex, request));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

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

    private static String correlationId(HttpServletRequest request) {
        String value = request.getHeader(CORRELATION_HEADER);
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
