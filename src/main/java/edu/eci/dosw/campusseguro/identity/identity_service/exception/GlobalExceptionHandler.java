package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.time.Instant;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.identity.identity_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    static final String REASON_WEAK_PASSWORD = "CONTRASENA_DEBIL";
    static final String REASON_INVALID_FORMAT = "FORMATO_INVALIDO";

    /** Opcional: sin publicador (p. ej. en pruebas con un manejador creado a mano) no se audita. */
    private AuditEventPublisher auditPublisher;

    @Autowired(required = false)
    public void setAuditPublisher(AuditEventPublisher auditPublisher) {
        this.auditPublisher = auditPublisher;
    }

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
     * RG-04: Se rechazan (HTTP 400) los campos no definidos en el contrato (en especial roles,
     * estado o id enviados por el usuario en registro y perfil).
    */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        if (isPasswordEndpoint(request)) {
            auditPasswordValidationFailure(request, REASON_INVALID_FORMAT);
            return passwordError(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos inválidos.", List.of(), request);
        }
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
        Object target = ex.getBindingResult().getTarget();
        if (target instanceof ForgotPasswordRequest || target instanceof ResetPasswordRequest
                || target instanceof ChangePasswordRequest) {
            boolean weakPassword = ex.getBindingResult().getFieldErrors().stream()
                    .anyMatch(e -> "nueva_contrasena".equals(e.getField()));
            auditPasswordValidationFailure(request, weakPassword ? REASON_WEAK_PASSWORD : REASON_INVALID_FORMAT);
            ErrorResponse body = normalizedValidationError(ex, request, CorrelationId.from(request));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .header(CorrelationId.HEADER, body.correlationId()).body(body);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /** RF-MS01-08 A1: codigo incorrecto, vencido o usado; mensaje generico (RN-MS01-20). */
    @ExceptionHandler(InvalidResetCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetCode(InvalidResetCodeException ex,
            HttpServletRequest request) {
        return passwordError(HttpStatus.BAD_REQUEST, "CODIGO_INVALIDO", ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ErrorResponse> handlePasswordMismatch(PasswordMismatchException ex,
            HttpServletRequest request) {
        return passwordFieldError("confirmar_nueva_contrasena", ex.getMessage(), request);
    }

    @ExceptionHandler(WrongCurrentPasswordException.class)
    public ResponseEntity<ErrorResponse> handleWrongCurrentPassword(WrongCurrentPasswordException ex,
            HttpServletRequest request) {
        return passwordFieldError("contrasena_actual", ex.getMessage(), request);
    }

    @ExceptionHandler(SameAsCurrentPasswordException.class)
    public ResponseEntity<ErrorResponse> handleSameAsCurrentPassword(SameAsCurrentPasswordException ex,
            HttpServletRequest request) {
        return passwordFieldError("nueva_contrasena", ex.getMessage(), request);
    }

    /** RN-MS01-23: faltan las cabeceras de identidad. */
    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleNotAuthenticated(NotAuthenticatedException ex,
            HttpServletRequest request) {
        return passwordError(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", ex.getMessage(), List.of(), request);
    }

    /** El rol de X-User-Role no tiene el permiso de la operacion. */
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermissionDenied(PermissionDeniedException ex,
            HttpServletRequest request) {
        return passwordError(HttpStatus.FORBIDDEN, "PROHIBIDO", ex.getMessage(), List.of(), request);
    }

    private ResponseEntity<ErrorResponse> passwordFieldError(String field, String message,
            HttpServletRequest request) {
        return passwordError(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos inválidos.",
                List.of(new ErrorResponse.ErrorDetail(field, message)), request);
    }

    /** ErrorResponse con los cinco campos del contrato y X-Correlation-ID tambien en la cabecera. */
    private ResponseEntity<ErrorResponse> passwordError(HttpStatus status, String codigo, String mensaje,
            List<ErrorResponse.ErrorDetail> errores, HttpServletRequest request) {
        String corr = CorrelationId.from(request);
        return ResponseEntity.status(status).header(CorrelationId.HEADER, corr)
                .body(new ErrorResponse(status.value(), codigo, mensaje, errores, corr));
    }

    /**
     * RF-MS01-08 A2 y RNF-MS01-10: una entrada invalida (contrasena que incumple la politica, formato o
     * campos mal formados) tambien se audita como FALLO. Nunca altera la respuesta (si MS08 falla, solo log).
     */
    private void auditPasswordValidationFailure(HttpServletRequest request, String detail) {
        if (auditPublisher == null) {
            return;
        }
        String path = request.getRequestURI();
        boolean authenticatedChange = "/api/v1/users/me/password".equals(path);
        AuditAction action = authenticatedChange ? AuditAction.CAMBIO_CONTRASENA : AuditAction.RECUPERACION;
        String corr = CorrelationId.from(request);
        try {
            UUID userId = null;
            if (authenticatedChange) {
                userId = parseUserId(request.getHeader("X-User-Id"));
            }
            auditPublisher.publish(new AuditEvent(action, AuditResult.FALLO, userId, null, detail, corr, Instant.now()));
        } catch (RuntimeException e) {
            log.error("[{}] Falló la emisión del evento de auditoría {}/FALLO: {}", corr, action, e.toString());
        }
    }

    private static UUID parseUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isPasswordEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && (path.equals("/api/v1/auth/password/forgot")
                || path.equals("/api/v1/auth/password/reset")
                || path.equals("/api/v1/users/me/password"));
    }

    /**
     * Organiza los mensajes de la excepcion, deja un solo mensaje por campo, y devuelve el error en formato DTO
     * @param ex la excepcion
     * @param request el request que que se envia con el error
     * @return el error en formato DTO (record en este caso)
     */
    private ErrorResponse normalizedValidationError(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return normalizedValidationError(ex, request, correlationId(request));
    }

    private ErrorResponse normalizedValidationError(MethodArgumentNotValidException ex, HttpServletRequest request,
            String correlationId) {
        Map<String, String> messageByField = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> messageByField.putIfAbsent(e.getField(), e.getDefaultMessage()));
        List<ErrorResponse.ErrorDetail> details = messageByField.entrySet().stream()
                .map(e -> new ErrorResponse.ErrorDetail(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ErrorResponse.ErrorDetail::campo))
                .toList();
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "VALIDACION", "Datos inválidos.", details,
                correlationId);
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