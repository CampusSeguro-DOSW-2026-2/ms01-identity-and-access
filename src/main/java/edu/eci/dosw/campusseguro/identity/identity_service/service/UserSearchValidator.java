package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * Valida los parámetros de GET /api/v1/users según el formulario F-07 y los convierte en un
 * UserSearchCriteria
 *
 * correo: 1 a 100 caracteres, sin etiquetas HTML
 *
 * estado y rol: solo valores del catálogo, omitir equivale a Todos
 *
 * page: entero no negativo, 0 por defecto
 *
 * size: entero de 1 a 100, 20 por defecto
 */
public final class UserSearchValidator {

    public static final String FIELD_EMAIL = "correo";
    public static final String FIELD_STATE = "estado";
    public static final String FIELD_ROLE = "rol";
    public static final String FIELD_PAGE = "page";
    public static final String FIELD_SIZE = "size";

    public static final String INVALID_SEARCH_MESSAGE = "Búsqueda no válida.";
    public static final String INVALID_STATE_MESSAGE = "Estado no válido.";
    public static final String INVALID_ROLE_MESSAGE = "Rol no válido.";
    public static final String INVALID_PAGE_MESSAGE = "Página no válida.";
    public static final String INVALID_SIZE_MESSAGE = "Tamaño de página no válido (1 a 100).";

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final int MAX_EMAIL_LENGTH = 100;

    private static final String ALL_VALUE = "TODOS";

    /**
     * No se instancia
     */
    private UserSearchValidator() {
    }

    /**
     * Valida los parámetros recibidos y los convierte en criterios de búsqueda
     *
     * @param email texto de búsqueda parcial por correo, puede ser null o vacío
     * @param state estado por el que se filtra, puede ser null, vacío o Todos
     * @param role rol por el que se filtra, puede ser null, vacío o Todos
     * @param page número de página recibido como texto, null equivale a 0
     * @param size tamaño de página recibido como texto, null equivale a 20
     * @return los criterios de búsqueda validados
     * @throws InvalidRequestException si algún parámetro incumple F-07, con un error por parámetro
     */
    public static UserSearchCriteria validate(String email, String state, String role, String page,
            String size) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();
        String text = parseEmail(email, errors);
        UserState stateFilter = parseFilter(state, UserState.class, FIELD_STATE, INVALID_STATE_MESSAGE, errors);
        UserRole roleFilter = parseFilter(role, UserRole.class, FIELD_ROLE, INVALID_ROLE_MESSAGE, errors);
        int pageNumber = parseNumber(page, DEFAULT_PAGE, 0, Integer.MAX_VALUE, FIELD_PAGE, INVALID_PAGE_MESSAGE,
                errors);
        int pageSize = parseNumber(size, DEFAULT_SIZE, 1, MAX_SIZE, FIELD_SIZE, INVALID_SIZE_MESSAGE, errors);

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        return new UserSearchCriteria(text, stateFilter, roleFilter, pageNumber, pageSize);
    }

    /**
     * Valida el texto de búsqueda por correo
     *
     * @param email texto recibido
     * @param errors lista donde se agrega el error si el texto no es válido
     * @return el texto recortado y en minúsculas, o null si no se filtra por correo
     */
    private static String parseEmail(String email, List<ErrorResponse.ErrorDetail> errors) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.length() > MAX_EMAIL_LENGTH || trimmed.contains("<") || trimmed.contains(">")) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_EMAIL, INVALID_SEARCH_MESSAGE));
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Valida un filtro que solo admite valores de un catálogo
     *
     * @param value valor recibido
     * @param type enumeración con los valores del catálogo
     * @param field nombre del parámetro, para reportar el error
     * @param message mensaje de error del parámetro
     * @param errors lista donde se agrega el error si el valor no es del catálogo
     * @param <E> tipo de la enumeración
     * @return el valor del catálogo, o null si se piden todos o el valor no es válido
     */
    private static <E extends Enum<E>> E parseFilter(String value, Class<E> type, String field, String message,
            List<ErrorResponse.ErrorDetail> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (ALL_VALUE.equalsIgnoreCase(trimmed)) {
            return null;
        }
        try {
            return Enum.valueOf(type, trimmed);
        } catch (IllegalArgumentException e) {
            errors.add(new ErrorResponse.ErrorDetail(field, message));
            return null;
        }
    }

    /**
     * Valida un parámetro numérico entero dentro de un rango
     *
     * @param value valor recibido como texto
     * @param defaultValue valor que se usa si el parámetro se omite
     * @param min valor mínimo permitido
     * @param max valor máximo permitido
     * @param field nombre del parámetro, para reportar el error
     * @param message mensaje de error del parámetro
     * @param errors lista donde se agrega el error si el valor no es válido
     * @return el número recibido, el valor por defecto si se omitió, o el valor por defecto si no es válido
     */
    private static int parseNumber(String value, int defaultValue, int min, int max, String field, String message,
            List<ErrorResponse.ErrorDetail> errors) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                errors.add(new ErrorResponse.ErrorDetail(field, message));
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.add(new ErrorResponse.ErrorDetail(field, message));
            return defaultValue;
        }
    }
}
