package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;
import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * Validates and normalizes the bodies and the paths of the role management according to forms F-10, F-11, F-12 and F-13
 * (RF-MS01-10)
 *
 * rol: it is trimmed and converted to uppercase before validating it (RG-08), from 2 to 30 characters: letters without
 * accents, digits and underscore, starting with a letter, and it cannot be the reserved name DETAILS
 *
 * descripcion: from 3 to 200 characters and without HTML tags (RG-03)
 *
 * permisos: list that may be empty or absent, without repeated ones and all of them from the database catalog, they are
 * not normalized (only the spaces are trimmed)
 *
 * any field not defined in the contract: "Campo no permitido."
 */
public final class RoleRequestValidator {

    public static final String FIELD_ROLE = "rol";
    public static final String FIELD_DESCRIPTION = "descripcion";
    public static final String FIELD_PERMISSIONS = "permisos";

    public static final String INVALID_NAME_MESSAGE = "El nombre del rol debe tener de 2 a 30 caracteres: "
            + "letras sin tilde, números y guion bajo, empezando con una letra.";
    public static final String RESERVED_NAME_MESSAGE = "Ese nombre de rol está reservado.";
    public static final String INVALID_PATH_ROLE_MESSAGE = "Rol no válido.";
    public static final String INVALID_DESCRIPTION_MESSAGE = "La descripción debe tener entre 3 y 200 caracteres.";
    public static final String INVALID_PERMISSION_MESSAGE = "Permiso no válido";
    public static final String REPEATED_PERMISSION_MESSAGE = "Permiso repetido";

    public static final int MIN_DESCRIPTION_LENGTH = 3;
    public static final int MAX_DESCRIPTION_LENGTH = 200;

    private static final Pattern SAFE_TO_ECHO = Pattern.compile("^[A-Za-z0-9_:.\\-]{1,60}$");

        /**
     * Utility class, it is not instantiated
     */
    private RoleRequestValidator() {
    }

        /**
     * Validates and normalizes the body of POST /api/v1/roles
     *
     * @param request body received
     * @param catalog codes of all the permissions of the catalog
     * @return the command with the normalized name, the description and the sorted permissions
     * @throws InvalidRequestException if any data breaks F-10 or a field that is not allowed arrives, with one error
     *         per field
     */
    public static CreateRoleCommand validateCreate(CreateRoleRequest request, Set<String> catalog) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>(request.unknownFieldErrors());
        String name = parseName(request.getRol(), errors);
        String description = parseDescription(request.getDescripcion(), errors);
        List<String> permissions = parsePermissions(request.getPermisos(), catalog, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        return new CreateRoleCommand(name, description, permissions);
    }

        /**
     * Validates and normalizes the body of PUT /api/v1/roles/{rol}
     *
     * @param request body received
     * @return the command with the normalized name and the description
     * @throws InvalidRequestException if any data breaks F-11 or a field that is not allowed arrives, with one error
     *         per field
     */
    public static UpdateRoleCommand validateUpdate(UpdateRoleRequest request) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>(request.unknownFieldErrors());
        String name = parseName(request.getRol(), errors);
        String description = parseDescription(request.getDescripcion(), errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        return new UpdateRoleCommand(name, description);
    }

        /**
     * Validates the body of PUT /api/v1/roles/{rol}/permissions
     *
     * @param request body received
     * @param catalog codes of all the permissions of the catalog
     * @return the command with the sorted permissions without repeated ones
     * @throws InvalidRequestException if any data breaks F-12 or a field that is not allowed arrives, with one error
     *         per field
     */
    public static RolePermissionsCommand validatePermissions(UpdateRolePermissionsRequest request,
            Set<String> catalog) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>(request.unknownFieldErrors());
        List<String> permissions = parsePermissions(request.getPermisos(), catalog, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        return new RolePermissionsCommand(permissions);
    }

        /**
     * Normalizes the name of a role received in a path (RG-08)
     *
     * @param rawRole value of {rol} in the path
     * @return the name in uppercase
     * @throws InvalidRequestException if the name does not have a valid format
     */
    public static String normalizePathRole(String rawRole) {
        String name = RoleNames.normalize(rawRole);
        if (name == null) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE,
                    List.of(new ErrorResponse.ErrorDetail(FIELD_ROLE, INVALID_PATH_ROLE_MESSAGE)));
        }
        return name;
    }

        /**
     * Normalizes the name of a role received in a body (RG-08)
     *
     * @param rawName name received
     * @param errors list where the error is added if the name does not have a valid format or is reserved
     * @return the name in uppercase, or null if it is not valid
     */
    private static String parseName(String rawName, List<ErrorResponse.ErrorDetail> errors) {
        String name = RoleNames.normalize(rawName);
        if (name == null) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_ROLE, INVALID_NAME_MESSAGE));
            return null;
        }
        if (RoleNames.isReserved(name)) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_ROLE, RESERVED_NAME_MESSAGE));
            return null;
        }
        return name;
    }

        /**
     * Validates the description of the role
     *
     * @param rawDescription description received
     * @param errors list where the error is added if the description is not valid
     * @return the trimmed description, or null if it is not valid
     */
    private static String parseDescription(String rawDescription, List<ErrorResponse.ErrorDetail> errors) {
        String description = Texts.trimToNull(rawDescription);
        boolean valid = description != null
                && description.length() >= MIN_DESCRIPTION_LENGTH
                && description.length() <= MAX_DESCRIPTION_LENGTH
                && !description.contains("<")
                && !description.contains(">");
        if (!valid) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_DESCRIPTION, INVALID_DESCRIPTION_MESSAGE));
            return null;
        }
        return description;
    }

        /**
     * Validates the list of permissions: from the catalog and without repeated ones, the codes are not normalized, an
     * empty or absent list is valid because the mandatory permissions are added or required by the service
     *
     * @param rawPermissions codes received
     * @param catalog codes of all the permissions of the catalog
     * @param errors list where the error of the first invalid or repeated code is added
     * @return the trimmed codes, sorted and without repeated ones
     */
    private static List<String> parsePermissions(List<String> rawPermissions, Set<String> catalog,
            List<ErrorResponse.ErrorDetail> errors) {
        if (rawPermissions == null) {
            return List.of();
        }

        TreeSet<String> codes = new TreeSet<>();
        for (String raw : rawPermissions) {
            String code = Texts.trimToNull(raw);
            if (code == null || !catalog.contains(code)) {
                errors.add(new ErrorResponse.ErrorDetail(FIELD_PERMISSIONS,
                        describe(INVALID_PERMISSION_MESSAGE, code)));
                return List.of();
            }
            if (!codes.add(code)) {
                errors.add(new ErrorResponse.ErrorDetail(FIELD_PERMISSIONS,
                        describe(REPEATED_PERMISSION_MESSAGE, code)));
                return List.of();
            }
        }
        return List.copyOf(codes);
    }

        /**
     * Builds the message of a rejected permission, the code is only repeated in the message if it has no characters
     * that are unsafe to return to the client
     *
     * @param message base message
     * @param code rejected code, may be null
     * @return the message with the code, or the message alone with a final period if the code is not safe
     */
    private static String describe(String message, String code) {
        if (code != null && SAFE_TO_ECHO.matcher(code).matches()) {
            return message + ": " + code;
        }
        return message + ".";
    }
}
