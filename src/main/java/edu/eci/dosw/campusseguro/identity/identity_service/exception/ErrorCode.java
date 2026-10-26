package edu.eci.dosw.campusseguro.identity.identity_service.exception;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo de estados y códigos de error de los Estándares de Integración (sección 7.2) que MS01 puede
 * devolver en el ErrorResponse, cada código conoce su estado HTTP, su texto del catálogo y un mensaje
 * genérico y seguro en español que se usa cuando el error lo genera el framework y no una regla de negocio
 *
 * Para cada estado HTTP el primer código declarado es el general, el que se usa cuando solo se conoce el
 * estado
 */
public enum ErrorCode {

    VALIDATION(400, "VALIDACION", Messages.INVALID_DATA),
    INVALID_CODE(400, "CODIGO_INVALIDO", Messages.INVALID_DATA),
    UNAUTHENTICATED(401, "NO_AUTENTICADO", Messages.UNAUTHENTICATED),
    BAD_CREDENTIALS(401, "CREDENCIALES_INCORRECTAS", Messages.UNAUTHENTICATED),
    FORBIDDEN(403, "PROHIBIDO", Messages.FORBIDDEN),
    INACTIVE_ACCOUNT(403, "CUENTA_INACTIVA", Messages.FORBIDDEN),
    NOT_FOUND(404, "NO_ENCONTRADO", "Recurso no encontrado."),
    METHOD_NOT_ALLOWED(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido."),
    CONFLICT(409, "CONFLICTO", "Conflicto con el estado actual del recurso."),
    UNSUPPORTED_MEDIA_TYPE(415, "TIPO_NO_SOPORTADO", "Tipo de contenido no soportado."),
    BUSINESS_RULE(422, "REGLA_NEGOCIO", "La operación no cumple una regla de negocio."),
    INTERNAL_ERROR(500, "ERROR_INTERNO", "Error interno del servidor.");

    private static final Map<Integer, ErrorCode> GENERAL_BY_STATUS = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    ErrorCode::getStatus,
                    Function.identity(),
                    (general, other) -> general));

    private final int status;
    private final String code;
    private final String message;

    /**
     * Crea un código del catálogo
     *
     * @param status estado HTTP con el que se responde
     * @param code texto del campo codigo del ErrorResponse
     * @param message mensaje genérico y seguro en español
     */
    ErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    /**
     * Obtiene el estado HTTP del código
     *
     * @return el estado HTTP con el que se responde
     */
    public int getStatus() {
        return status;
    }

    /**
     * Obtiene el texto del campo codigo del ErrorResponse
     *
     * @return el código del catálogo en MAYUSCULAS_CON_GUION_BAJO
     */
    public String getCode() {
        return code;
    }

    /**
     * Obtiene el mensaje genérico y seguro del código
     *
     * @return el mensaje genérico en español
     */
    public String getMessage() {
        return message;
    }

    /**
     * Devuelve el código general que corresponde a un estado HTTP, los estados 4xx que el catálogo no
     * contempla se tratan como VALIDATION y los 5xx como INTERNAL_ERROR
     *
     * @param status estado HTTP de la respuesta
     * @return el código general del estado
     */
    public static ErrorCode forStatus(int status) {
        ErrorCode general = GENERAL_BY_STATUS.get(status);
        if (general != null) {
            return general;
        }
        if (status >= INTERNAL_ERROR.status) {
            return INTERNAL_ERROR;
        }
        return VALIDATION;
    }

    /**
     * Mensajes que comparten varios códigos del catálogo
     */
    private static final class Messages {

        static final String INVALID_DATA = "Datos inválidos.";
        static final String UNAUTHENTICATED = "No autenticado.";
        static final String FORBIDDEN = "No cuenta con los permisos suficientes para realizar esta operación.";

        /**
         * Clase de utilidad, no se instancia
         */
        private Messages() {
        }
    }
}
