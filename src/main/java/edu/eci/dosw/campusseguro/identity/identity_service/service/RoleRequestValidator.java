package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.Permission;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.util.RoleNames;
import edu.eci.dosw.campusseguro.identity.identity_service.util.Texts;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * Valida y normaliza los cuerpos y las rutas de la gestión de roles según los formularios F-10, F-11, F-12 y
 * F-13 (RF-MS01-10)
 *
 * rol: se recorta y se pasa a mayúsculas antes de validarlo (RG-08), de 2 a 30 caracteres: letras sin tilde,
 * dígitos y guion bajo, empezando con una letra, y no puede ser el nombre reservado DETAILS
 *
 * descripcion: de 3 a 200 caracteres y sin etiquetas HTML (RG-03)
 *
 * permisos: al menos uno, sin repetidos y todos del catálogo, no se normalizan (solo se recortan los espacios)
 *
 * cualquier campo no definido en el contrato: "Campo no permitido."
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
    public static final String NO_PERMISSIONS_MESSAGE = "Seleccione al menos un permiso.";
    public static final String INVALID_PERMISSION_MESSAGE = "Permiso no válido";
    public static final String REPEATED_PERMISSION_MESSAGE = "Permiso repetido";

    public static final int MIN_DESCRIPTION_LENGTH = 3;
    public static final int MAX_DESCRIPTION_LENGTH = 200;

    private static final Pattern SAFE_TO_ECHO = Pattern.compile("^[A-Za-z0-9_:.\\-]{1,60}$");

    /**
     * Clase de utilidad, no se instancia
     */
    private RoleRequestValidator() {
    }

    /**
     * Valida y normaliza el cuerpo de POST /api/v1/roles
     *
     * @param request cuerpo recibido
     * @return el comando con el nombre normalizado, la descripción y los permisos ordenados
     * @throws InvalidRequestException si algún dato incumple F-10 o llega un campo no permitido, con un
     *         error por campo
     */
    public static CreateRoleCommand validateCreate(CreateRoleRequest request) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>(request.unknownFieldErrors());
        String name = parseName(request.getRol(), errors);
        String description = parseDescription(request.getDescripcion(), errors);
        List<String> permissions = parsePermissions(request.getPermisos(), errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        return new CreateRoleCommand(name, description, permissions);
    }

    /**
     * Valida y normaliza el cuerpo de PUT /api/v1/roles/{rol}
     *
     * @param request cuerpo recibido
     * @return el comando con el nombre normalizado y la descripción
     * @throws InvalidRequestException si algún dato incumple F-11 o llega un campo no permitido, con un
     *         error por campo
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
     * Valida el cuerpo de PUT /api/v1/roles/{rol}/permissions
     *
     * @param request cuerpo recibido
     * @return el comando con los permisos ordenados y sin repetidos
     * @throws InvalidRequestException si algún dato incumple F-12 o llega un campo no permitido, con un
     *         error por campo
     */
    public static RolePermissionsCommand validatePermissions(UpdateRolePermissionsRequest request) {
        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>(request.unknownFieldErrors());
        List<String> permissions = parsePermissions(request.getPermisos(), errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(ValidationErrors.INVALID_DATA_MESSAGE, ValidationErrors.merge(errors));
        }
        return new RolePermissionsCommand(permissions);
    }

    /**
     * Normaliza el nombre de un rol recibido en una ruta (RG-08)
     *
     * @param rawRole valor de {rol} en la ruta
     * @return el nombre en mayúsculas
     * @throws InvalidRequestException si el nombre no tiene un formato válido
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
     * Normaliza el nombre de un rol recibido en un cuerpo (RG-08)
     *
     * @param rawName nombre recibido
     * @param errors lista donde se agrega el error si el nombre no tiene un formato válido o está reservado
     * @return el nombre en mayúsculas, o null si no es válido
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
     * Valida la descripción del rol
     *
     * @param rawDescription descripción recibida
     * @param errors lista donde se agrega el error si la descripción no es válida
     * @return la descripción recortada, o null si no es válida
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
     * Valida la lista de permisos: al menos uno, del catálogo y sin repetidos, los códigos no se normalizan
     *
     * @param rawPermissions códigos recibidos
     * @param errors lista donde se agrega el error del primer código inválido o repetido
     * @return los códigos recortados, ordenados y sin repetidos
     */
    private static List<String> parsePermissions(List<String> rawPermissions,
            List<ErrorResponse.ErrorDetail> errors) {
        if (rawPermissions == null || rawPermissions.isEmpty()) {
            errors.add(new ErrorResponse.ErrorDetail(FIELD_PERMISSIONS, NO_PERMISSIONS_MESSAGE));
            return List.of();
        }

        TreeSet<String> codes = new TreeSet<>();
        for (String raw : rawPermissions) {
            String code = Texts.trimToNull(raw);
            if (code == null || Permission.fromCode(code).isEmpty()) {
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
     * Arma el mensaje de un permiso rechazado, el código solo se repite en el mensaje si no tiene caracteres
     * que sea inseguro devolver al cliente
     *
     * @param message mensaje base
     * @param code código rechazado, puede ser null
     * @return el mensaje con el código, o el mensaje solo con un punto final si el código no es seguro
     */
    private static String describe(String message, String code) {
        if (code != null && SAFE_TO_ECHO.matcher(code).matches()) {
            return message + ": " + code;
        }
        return message + ".";
    }
}
