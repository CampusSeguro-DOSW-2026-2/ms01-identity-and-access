package edu.eci.dosw.campusseguro.identity.identity_service.validation;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.validation.FieldError;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import jakarta.validation.ConstraintViolation;

/**
 * Convierte los resultados de la validación en los errores por campo del ErrorResponse (RG-05, RG-06),
 * el campo se informa con el nombre que tiene en el contrato y no con el de la clase Java, y se deja un
 * solo mensaje por campo
 */
public final class ValidationErrors {

    public static final String INVALID_DATA_MESSAGE = ErrorCode.VALIDATION.getMessage();

    /**
     * Clase de utilidad, no se instancia
     */
    private ValidationErrors() {
    }

    /**
     * Convierte las violaciones de Bean Validation en errores por campo
     *
     * @param type clase del objeto validado, de la que se toman los nombres del contrato
     * @param violations violaciones encontradas
     * @return errores por campo, uno por campo y ordenados por nombre
     */
    public static List<ErrorResponse.ErrorDetail> fromViolations(Class<?> type,
            Collection<? extends ConstraintViolation<?>> violations) {
        Map<String, String> messageByField = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : violations) {
            String field = wireName(type, violation.getPropertyPath().toString());
            messageByField.putIfAbsent(field, violation.getMessage());
        }
        return toSortedDetails(messageByField);
    }

    /**
     * Convierte los errores de campo de Spring en errores por campo del contrato
     *
     * @param type clase del objeto validado, de la que se toman los nombres del contrato
     * @param fieldErrors errores de campo encontrados por Spring
     * @return errores por campo, uno por campo y ordenados por nombre
     */
    public static List<ErrorResponse.ErrorDetail> fromFieldErrors(Class<?> type, List<FieldError> fieldErrors) {
        Map<String, String> messageByField = new LinkedHashMap<>();
        for (FieldError error : fieldErrors) {
            String field = wireName(type, error.getField());
            messageByField.putIfAbsent(field, error.getDefaultMessage());
        }
        return toSortedDetails(messageByField);
    }

    /**
     * Une varias listas de errores por campo dejando un solo mensaje por campo, el primero que se
     * encuentre, y ordenándolos por nombre de campo
     *
     * @param errors errores por campo, posiblemente con campos repetidos
     * @return errores por campo sin repetidos y ordenados por nombre
     */
    public static List<ErrorResponse.ErrorDetail> merge(List<ErrorResponse.ErrorDetail> errors) {
        Map<String, String> messageByField = new LinkedHashMap<>();
        for (ErrorResponse.ErrorDetail error : errors) {
            messageByField.putIfAbsent(error.campo(), error.mensaje());
        }
        return toSortedDetails(messageByField);
    }

    /**
     * Obtiene el nombre que un campo tiene en el contrato
     *
     * @param type clase que declara el campo
     * @param path ruta de la propiedad validada, por ejemplo firstName o ids[0]
     * @return el valor de JsonProperty del campo, o el nombre del primer tramo de la ruta si no tiene uno
     */
    static String wireName(Class<?> type, String path) {
        String name = firstSegment(path);
        Class<?> current = type;
        while (current != null) {
            for (Field declared : current.getDeclaredFields()) {
                if (declared.getName().equals(name)) {
                    JsonProperty annotation = declared.getAnnotation(JsonProperty.class);
                    if (annotation != null && !annotation.value().isEmpty()) {
                        return annotation.value();
                    }
                    return name;
                }
            }
            current = current.getSuperclass();
        }
        return name;
    }

    /**
     * Obtiene el primer tramo de una ruta de propiedad, sin índices ni subpropiedades
     *
     * @param path ruta de la propiedad validada
     * @return el nombre de la propiedad de primer nivel
     */
    private static String firstSegment(String path) {
        int end = path.length();
        int bracket = path.indexOf('[');
        if (bracket >= 0 && bracket < end) {
            end = bracket;
        }
        int dot = path.indexOf('.');
        if (dot >= 0 && dot < end) {
            end = dot;
        }
        return path.substring(0, end);
    }

    /**
     * Ordena por nombre de campo y convierte a la representación del contrato
     *
     * @param messageByField mensaje de cada campo
     * @return la lista de errores por campo ordenada por nombre de campo
     */
    private static List<ErrorResponse.ErrorDetail> toSortedDetails(Map<String, String> messageByField) {
        return messageByField.entrySet().stream()
                .map(entry -> new ErrorResponse.ErrorDetail(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ErrorResponse.ErrorDetail::campo))
                .toList();
    }
}
