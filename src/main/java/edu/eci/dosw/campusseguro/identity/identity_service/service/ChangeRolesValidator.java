package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * Validates the body of PATCH /api/v1/users/roles according to form F-09 and converts it into a ChangeRolesCommand
 *
 * ids: at least one user and all of them with a valid identifier format
 *
 * agregar / retirar: role names with valid format and without repeats, that they exist is checked by the service
 * against the roles table
 *
 * Ambas listas vacias: "Indique roles a agregar o retirar."
 *
 * A role in both lists: "Un rol no puede agregarse y retirarse a la vez."
 */
public final class ChangeRolesValidator {

    public static final String FIELD_IDS = "ids";
    public static final String FIELD_ADD = "agregar";
    public static final String FIELD_REMOVE = "retirar";

    public static final String INVALID_DATA_MESSAGE = ErrorCode.VALIDATION.getMessage();
    public static final String MISSING_USERS_MESSAGE = "Seleccione al menos un usuario.";
    public static final String INVALID_USER_ID_MESSAGE = "Identificador de usuario no válido.";
    public static final String INVALID_ROLES_MESSAGE = "Seleccione roles válidos.";
    public static final String NO_ROLES_GIVEN_MESSAGE = "Indique roles a agregar o retirar.";
    public static final String ROLE_IN_BOTH_LISTS_MESSAGE = "Un rol no puede agregarse y retirarse a la vez.";

    private static final Pattern UUID_FORMAT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

        /**
     * Utility class, it is not instantiated
     */
    private ChangeRolesValidator() {
    }

        /**
     * Validates the request and converts it into a command with strong types
     *
     * @param request body received in PATCH /api/v1/users/roles
     * @return the command with the users, the roles to add and to remove
     * @throws InvalidRequestException if any data breaks F-09 and HTTP 400 is thrown
     */
    public static ChangeRolesCommand validate(ChangeRolesRequest request) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>(request.unknownFieldErrors());
        Set<UUID> userIds = parseUserIds(request.getIds(), errors);
        Set<String> rolesToAdd = parseRoles(FIELD_ADD, request.getAgregar(), errors);
        Set<String> rolesToRemove = parseRoles(FIELD_REMOVE, request.getRetirar(), errors);

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        if (rolesToAdd.isEmpty() && rolesToRemove.isEmpty()) {
            throw new InvalidRequestException(NO_ROLES_GIVEN_MESSAGE, List.of());
        }
        if (hasCommonRole(rolesToAdd, rolesToRemove)) {
            throw new InvalidRequestException(ROLE_IN_BOTH_LISTS_MESSAGE, List.of());
        }
        return new ChangeRolesCommand(userIds, rolesToAdd, rolesToRemove);
    }

        /**
     * Converts the received list of ids into a set of UUID without repeated ones and in the same order
     *
     * @param rawIds ids received
     * @param errors list where the errors by field found are added
     * @return ordered set of valid identifiers
     */
    private static Set<UUID> parseUserIds(List<String> rawIds, List<ErrorResponse.ErrorDetail> errors) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (rawIds == null || rawIds.isEmpty()) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_IDS, MISSING_USERS_MESSAGE));
            return ids;
        }

        boolean anyInvalid = false;
        for (String rawId : rawIds) {
            UUID parsed = parseUuid(rawId);
            if (parsed == null) {
                anyInvalid = true;
            } else {
                ids.add(parsed);
            }
        }
        if (anyInvalid) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_IDS, INVALID_USER_ID_MESSAGE));
        }
        return ids;
    }

        /**
     * Converts a received identifier into a UUID
     *
     * @param rawId identifier received
     * @return the UUID, or null if the value is empty or does not have UUID format
     */
    private static UUID parseUuid(String rawId) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        if (!UUID_FORMAT.matcher(trimmed).matches()) {
            return null;
        }
        return UUID.fromString(trimmed);
    }

        /**
     * Converts a list of role names into a set of names, the name is trimmed but not normalized: it must arrive exactly
     * as it is stored
     *
     * @param field name of the field of the request, to report errors if necessary
     * @param rawRoles role names received
     * @param errors list where the error by field is added if there are invalid or repeated names
     * @return set of role names with valid format, in the order received
     */
    private static Set<String> parseRoles(String field, List<String> rawRoles,
            List<ErrorResponse.ErrorDetail> errors) {
        Set<String> roles = new LinkedHashSet<>();
        if (rawRoles == null) {
            return roles;
        }

        boolean anyInvalid = false;
        for (String rawRole : rawRoles) {
            String name = null;
            if (rawRole != null) {
                name = rawRole.trim();
            }
            if (!RoleNames.hasStoredFormat(name)) {
                anyInvalid = true;
            } else if (!roles.add(name)) {
                anyInvalid = true;
            }
        }
        if (anyInvalid) {
            errors.add(new ErrorResponse.ErrorDetail(field, INVALID_ROLES_MESSAGE));
        }
        return roles;
    }

        /**
     * Tells whether two sets of roles share any role
     *
     * @param first first set of roles
     * @param second second set of roles
     * @return true if at least one role is present in both sets
     */
    private static boolean hasCommonRole(Set<String> first, Set<String> second) {
        Set<String> common = new LinkedHashSet<>(first);
        common.retainAll(second);
        return !common.isEmpty();
    }
}
