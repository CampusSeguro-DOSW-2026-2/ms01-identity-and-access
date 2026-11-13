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
 * Valida el cuerpo de PATCH /api/v1/users/roles segun el formulario F-09 y lo convierte en un
 * ChangeRolesCommand
 *
 * ids: al menos un usuario y todos con formato de identificador valido
 *
 * agregar / retirar: nombres de rol con formato valido y sin repetir, que existan lo comprueba el servicio
 * contra la tabla roles
 *
 * Ambas listas vacias: "Indique roles a agregar o retirar."
 *
 * Un rol en ambas listas: "Un rol no puede agregarse y retirarse a la vez."
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
     * Clase de utilidad, no se instancia
     */
    private ChangeRolesValidator() {
    }

    /**
     * Valida la peticion y la convierte en un comando con tipos fuertes
     *
     * @param request cuerpo recibido en PATCH /api/v1/users/roles
     * @return el comando con los usuarios, los roles a agregar y a retirar
     * @throws InvalidRequestException si algun dato incumple F-09 y se lanza HTTP 400
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
     * Convierte la lista de ids recibida en un conjunto de UUID sin repetidos y en el mismo orden
     *
     * @param rawIds ids recibidos
     * @param errors lista donde se agregan los errores por campo que se encuentren
     * @return conjunto ordenado de identificadores validos
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
     * Convierte un identificador recibido en UUID
     *
     * @param rawId identificador recibido
     * @return el UUID, o null si el valor esta vacio o no tiene formato UUID
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
     * Convierte una lista de nombres de rol en un conjunto de nombres, el nombre se recorta pero no se
     * normaliza: debe llegar exactamente como se guarda
     *
     * @param field nombre del campo de la peticion, para reportar errores si es necesario
     * @param rawRoles nombres de rol recibidos
     * @param errors lista donde se agrega el error por campo si hay nombres invalidos o repetidos
     * @return conjunto de nombres de rol con formato valido, en el orden recibido
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
     * Indica si dos conjuntos de roles comparten algun rol
     *
     * @param first primer conjunto de roles
     * @param second segundo conjunto de roles
     * @return true si existe al menos un rol presente en ambos conjuntos
     */
    private static boolean hasCommonRole(Set<String> first, Set<String> second) {
        Set<String> common = new LinkedHashSet<>(first);
        common.retainAll(second);
        return !common.isEmpty();
    }
}
