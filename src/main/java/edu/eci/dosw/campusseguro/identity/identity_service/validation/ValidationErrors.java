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
 * Converts the validation results into the errors by field of the ErrorResponse (RG-05, RG-06), the field is reported
 * with the name it has in the contract and not the one of the Java class, and a single message is left per field
 */
public final class ValidationErrors {

    public static final String INVALID_DATA_MESSAGE = ErrorCode.VALIDATION.getMessage();

        /**
     * Utility class, it is not instantiated
     */
    private ValidationErrors() {
    }

        /**
     * Converts the Bean Validation violations into errors by field
     *
     * @param type class of the validated object, from which the contract names are taken
     * @param violations violations found
     * @return errors by field, one per field and sorted by name
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
     * Converts the Spring field errors into errors by field of the contract
     *
     * @param type class of the validated object, from which the contract names are taken
     * @param fieldErrors field errors found by Spring
     * @return errors by field, one per field and sorted by name
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
     * Joins several lists of errors by field leaving a single message per field, the first one found, and sorting them
     * by field name
     *
     * @param errors errors by field, possibly with repeated fields
     * @return errors by field without repeated ones and sorted by name
     */
    public static List<ErrorResponse.ErrorDetail> merge(List<ErrorResponse.ErrorDetail> errors) {
        Map<String, String> messageByField = new LinkedHashMap<>();
        for (ErrorResponse.ErrorDetail error : errors) {
            messageByField.putIfAbsent(error.campo(), error.mensaje());
        }
        return toSortedDetails(messageByField);
    }

        /**
     * Gets the name that a field has in the contract
     *
     * @param type class that declares the field
     * @param path path of the validated property, for example firstName or ids[0]
     * @return the JsonProperty value of the field, or the name of the first segment of the path if it has none
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
     * Gets the first segment of a property path, without indexes or subproperties
     *
     * @param path path of the validated property
     * @return the name of the first-level property
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
     * Sorts by field name and converts to the representation of the contract
     *
     * @param messageByField message of each field
     * @return the list of errors by field sorted by field name
     */
    private static List<ErrorResponse.ErrorDetail> toSortedDetails(Map<String, String> messageByField) {
        return messageByField.entrySet().stream()
                .map(entry -> new ErrorResponse.ErrorDetail(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ErrorResponse.ErrorDetail::campo))
                .toList();
    }
}
