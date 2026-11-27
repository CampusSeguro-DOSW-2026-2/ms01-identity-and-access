package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * Validates the parameters of GET /api/v1/users according to form F-07 and converts them into a UserSearchCriteria
 *
 * correo: 1 to 100 characters, without HTML tags
 *
 * estado: only values from the catalog, omitting it is equivalent to Todos
 *
 * rol: name with a valid format, omitting it is equivalent to Todos, whether the role exists is checked by the service
 *
 * page: non-negative integer, 0 by default
 *
 * size: integer from 1 to 100, 20 by default
 */
public final class UserSearchValidator {

    public static final String FIELD_EMAIL = "correo";
    public static final String FIELD_STATE = "estado";
    public static final String FIELD_ROLE = "rol";
    public static final String FIELD_PAGE = "page";
    public static final String FIELD_SIZE = "size";
    public static final String FIELD_CREATED_BEFORE = "creado_antes";

    public static final String INVALID_SEARCH_MESSAGE = "Búsqueda no válida.";
    public static final String INVALID_STATE_MESSAGE = "Estado no válido.";
    public static final String INVALID_ROLE_MESSAGE = "Rol no válido.";
    public static final String INVALID_PAGE_MESSAGE = "Página no válida.";
    public static final String INVALID_SIZE_MESSAGE = "Tamaño de página no válido (1 a 100).";
    public static final String INVALID_CREATED_BEFORE_MESSAGE = "Fecha de corte no válida.";

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final int MAX_EMAIL_LENGTH = 100;

    private static final String ALL_VALUE = "TODOS";

        /**
     * It is not instantiated
     */
    private UserSearchValidator() {
    }

        /**
     * Validates the received parameters and converts them into search criteria
     *
     * @param email partial search text by email, may be null or empty
     * @param state status by which it is filtered, may be null, empty or Todos
     * @param role role by which it is filtered, may be null, empty or Todos
     * @param page page number received as text, null is equivalent to 0
     * @param size page size received as text, null is equivalent to 20
     * @return the validated search criteria
     * @throws InvalidRequestException if any parameter breaks F-07, with one error per parameter
     */
    public static UserSearchCriteria validate(String email, String state, String role, String page,
            String size) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();
        String text = parseEmail(email, errors);
        UserState stateFilter = parseFilter(state, UserState.class, FIELD_STATE, INVALID_STATE_MESSAGE, errors);
        String roleFilter = parseOptionalRole(role, errors);
        int pageNumber = parseNumber(page, DEFAULT_PAGE, 0, Integer.MAX_VALUE, FIELD_PAGE, INVALID_PAGE_MESSAGE,
                errors);
        int pageSize = parseNumber(size, DEFAULT_SIZE, 1, MAX_SIZE, FIELD_SIZE, INVALID_SIZE_MESSAGE, errors);

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        return new UserSearchCriteria(text, stateFilter, roleFilter, pageNumber, pageSize);
    }

        /**
     * Validates the parameters of the internal query by role (RF-MS01-09) and converts them into criteria, the
     * pagination follows the same rules as form F-07
     *
     * @param role required role, only values from the catalog
     * @param createdBefore cut-off instant in ISO 8601, may be null or empty
     * @param page page number received as text, null is equivalent to 0
     * @param size page size received as text, null is equivalent to 20
     * @return the validated criteria
     * @throws InvalidRequestException if any parameter is not valid, with one error per parameter
     */
    public static RoleSearchCriteria validateByRole(String role, String createdBefore, String page, String size) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();
        String roleValue = parseRequiredRole(role, errors);
        Instant cutoff = parseInstant(createdBefore, errors);
        int pageNumber = parseNumber(page, DEFAULT_PAGE, 0, Integer.MAX_VALUE, FIELD_PAGE, INVALID_PAGE_MESSAGE,
                errors);
        int pageSize = parseNumber(size, DEFAULT_SIZE, 1, MAX_SIZE, FIELD_SIZE, INVALID_SIZE_MESSAGE, errors);

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        return new RoleSearchCriteria(roleValue, cutoff, pageNumber, pageSize);
    }

        /**
     * Validates a required role, unlike the list filter it does not accept Todos
     *
     * @param role value received
     * @param errors list where the error is added if the role is missing or does not have a role format
     * @return the trimmed role name, or null if it is not valid
     */
    private static String parseRequiredRole(String role, List<ErrorResponse.ErrorDetail> errors) {
        if (role != null && RoleNames.hasStoredFormat(role.trim())) {
            return role.trim();
        }
        errors.add(new ErrorResponse.ErrorDetail(FIELD_ROLE, INVALID_ROLE_MESSAGE));
        return null;
    }

        /**
     * Validates the optional filter by role, the name must arrive exactly as it is stored
     *
     * @param role value received
     * @param errors list where the error is added if the value does not have a role format
     * @return the trimmed role name, or null if all are requested or the value is not valid
     */
    private static String parseOptionalRole(String role, List<ErrorResponse.ErrorDetail> errors) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String trimmed = role.trim();
        if (ALL_VALUE.equalsIgnoreCase(trimmed)) {
            return null;
        }
        if (!RoleNames.hasStoredFormat(trimmed)) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_ROLE, INVALID_ROLE_MESSAGE));
            return null;
        }
        return trimmed;
    }

        /**
     * Validates the optional cut-off instant
     *
     * @param value value received in ISO 8601, for example 2026-11-07T15:04:05Z
     * @param errors list where the error is added if the value is not a valid instant
     * @return the instant, or null if it was omitted or is not valid
     */
    private static Instant parseInstant(String value, List<ErrorResponse.ErrorDetail> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_CREATED_BEFORE, INVALID_CREATED_BEFORE_MESSAGE));
            return null;
        }
    }

        /**
     * Validates the search text by email
     *
     * @param email text received
     * @param errors list where the error is added if the text is not valid
     * @return the trimmed text in lowercase, or null if it is not filtered by email
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
     * Validates a filter that only accepts values from a catalog
     *
     * @param value value received
     * @param type enumeration with the values of the catalog
     * @param field name of the parameter, to report the error
     * @param message error message of the parameter
     * @param errors list where the error is added if the value is not from the catalog
     * @param <E> type of the enumeration
     * @return the value from the catalog, or null if all are requested or the value is not valid
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
     * Validates an integer numeric parameter within a range
     *
     * @param value value received as text
     * @param defaultValue value used if the parameter is omitted
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @param field name of the parameter, to report the error
     * @param message error message of the parameter
     * @param errors list where the error is added if the value is not valid
     * @return the number received, the default value if it was omitted, or the default value if it is not valid
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
