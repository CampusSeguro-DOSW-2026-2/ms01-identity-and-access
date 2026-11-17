package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.CreateRoleCommand;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RolePermissionsCommand;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleRequestValidator;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UpdateRoleCommand;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.ValidationErrors;

/**
 * Validations of forms F-10, F-11 and F-12 (RF-MS01-10) and of the {rol} path: normalized name, description from 3 to
 * 200 characters without tags, permissions of the database catalog without repeated ones, unknown fields
 */
class RoleRequestValidatorTest {

    private static final String PERM_A = "ms04:checkin:write";
    private static final String PERM_B = "ms04:checkin:read_own";
    private static final String PERM_C = "ms01:profile:read";
    private static final Set<String> CATALOG = Set.of(PERM_A, PERM_B, PERM_C);

        /**
     * Creation body
     *
     * @param name name of the role
     * @param description description of the role
     * @param permissions permissions of the role
     * @return the request
     */
    private CreateRoleRequest create(String name, String description, List<String> permissions) {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setRol(name);
        request.setDescripcion(description);
        request.setPermisos(permissions);
        return request;
    }

        /**
     * Edit body
     *
     * @param name name of the role
     * @param description description of the role
     * @return the request
     */
    private UpdateRoleRequest update(String name, String description) {
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setRol(name);
        request.setDescripcion(description);
        return request;
    }

        /**
     * Permission change body
     *
     * @param permissions permissions of the role
     * @return the request
     */
    private UpdateRolePermissionsRequest permissions(List<String> permissions) {
        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermisos(permissions);
        return request;
    }

        /**
     * Validates a creation that must fail
     *
     * @param request invalid request
     * @return the thrown exception
     */
    private InvalidRequestException invalidCreate(CreateRoleRequest request) {
        return assertThrows(InvalidRequestException.class, () -> RoleRequestValidator.validateCreate(request, CATALOG));
    }

        /**
     * Fields with error of an exception, in the order in which they arrived
     *
     * @param error validation exception
     * @return the field names
     */
    private List<String> fields(InvalidRequestException error) {
        List<String> fields = new ArrayList<>();
        for (ErrorResponse.ErrorDetail detail : error.getErrors()) {
            fields.add(detail.campo());
        }
        return fields;
    }

    // ------------------------------------------------------------------ creation (F-10)

        /**
     * A correct creation is normalized: name in uppercase, trimmed description and sorted permissions
     */
    @Test
    void validateCreate_validRequest_returnsTheNormalizedCommand() {
        CreateRoleCommand command = RoleRequestValidator.validateCreate(
                create("  monitor_evacuacion ", "  Apoya la evacuación  ", List.of(PERM_A, PERM_C, PERM_B)), CATALOG);

        assertEquals("MONITOR_EVACUACION", command.name());
        assertEquals("Apoya la evacuación", command.description());
        assertEquals(List.of(PERM_C, PERM_B, PERM_A), command.permissions());
    }

        /**
     * The length limits are accepted: name of 2 and of 30 characters, description of 3 and of 200
     */
    @Test
    void validateCreate_lengthLimits_areAccepted() {
        CreateRoleCommand shortest = RoleRequestValidator.validateCreate(create("AB", "abc", List.of(PERM_A)), CATALOG);
        CreateRoleCommand longest = RoleRequestValidator.validateCreate(
                create("A".repeat(30), "d".repeat(200), List.of(PERM_A)), CATALOG);

        assertEquals("AB", shortest.name());
        assertEquals(30, longest.name().length());
        assertEquals(200, longest.description().length());
    }

        /**
     * Names that break the input format: from 2 to 30 characters, letters without accents, numbers and underscore,
     * starting with a letter
     *
     * @param name invalid name
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "A", "1ROL", "_ROL", "ROL-MALO", "ROL MALO", "ROLÑ", "ROLÁ", "ROL.X",
            "ROL:X", "A1234567890123456789012345678901"})
    void validateCreate_invalidName_returnsTheNameError(String name) {
        InvalidRequestException error = invalidCreate(create(name, "Descripción", List.of(PERM_A)));

        assertEquals(List.of("rol"), fields(error));
        assertEquals(RoleRequestValidator.INVALID_NAME_MESSAGE, error.getErrors().get(0).mensaje());
    }

        /**
     * DETAILS is reserved for the route GET /roles/details: in any combination of uppercase it is rejected on creation
     * with an error in the rol field
     *
     * @param name reserved name
     */
    @ParameterizedTest
    @ValueSource(strings = {"DETAILS", "details", "Details", "  details  "})
    void validateCreate_reservedName_returnsTheReservedNameError(String name) {
        InvalidRequestException error = invalidCreate(create(name, "Descripción", List.of(PERM_A)));

        assertEquals(List.of("rol"), fields(error));
        assertEquals(RoleRequestValidator.RESERVED_NAME_MESSAGE, error.getErrors().get(0).mensaje());
    }

        /**
     * Only the exact name is reserved: the names that contain it are accepted
     */
    @Test
    void validateCreate_namesContainingDetails_areAccepted() {
        assertEquals("DETAILS_X", RoleRequestValidator.validateCreate(
                create("details_x", "Descripción", List.of(PERM_A)), CATALOG).name());
        assertEquals("MY_DETAILS", RoleRequestValidator.validateCreate(
                create("my_details", "Descripción", List.of(PERM_A)), CATALOG).name());
        assertEquals("DETAIL", RoleRequestValidator.validateCreate(
                create("detail", "Descripción", List.of(PERM_A)), CATALOG).name());
    }

        /**
     * An absent name is an invalid name
     */
    @Test
    void validateCreate_nullName_returnsTheNameError() {
        InvalidRequestException error = invalidCreate(create(null, "Descripción", List.of(PERM_A)));

        assertEquals(List.of("rol"), fields(error));
    }

        /**
     * Descriptions outside the rules: empty, short, long or with HTML tags (RG-03)
     *
     * @param description invalid description
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "ab", "  ab  ", "<b>negrita</b>", "texto > otro", "texto < otro"})
    void validateCreate_invalidDescription_returnsTheDescriptionError(String description) {
        InvalidRequestException error = invalidCreate(create("ROL", description, List.of(PERM_A)));

        assertEquals(List.of("descripcion"), fields(error));
        assertEquals(RoleRequestValidator.INVALID_DESCRIPTION_MESSAGE, error.getErrors().get(0).mensaje());
    }

        /**
     * A description of more than 200 characters is rejected, and an absent one too
     */
    @Test
    void validateCreate_tooLongOrMissingDescription_isRejected() {
        assertEquals(List.of("descripcion"),
                fields(invalidCreate(create("ROL", "d".repeat(201), List.of(PERM_A)))));
        assertEquals(List.of("descripcion"), fields(invalidCreate(create("ROL", null, List.of(PERM_A)))));
    }

    /**
     * Without permissions, or with an empty list, the request is valid: the service adds the mandatory ones
     */
    @Test
    void validateCreate_withoutPermissions_isValidWithAnEmptyList() {
        CreateRoleCommand missing = RoleRequestValidator.validateCreate(create("ROL", "Descripción", null), CATALOG);
        CreateRoleCommand empty = RoleRequestValidator.validateCreate(create("ROL", "Descripción", List.of()),
                CATALOG);

        assertEquals(List.of(), missing.permissions());
        assertEquals(List.of(), empty.permissions());
    }

        /**
     * A permission outside the catalog is rejected and its code is repeated in the message
     */
    @Test
    void validateCreate_permissionOutsideTheCatalog_isRejectedWithItsCode() {
        InvalidRequestException error = invalidCreate(create("ROL", "Descripción", List.of(PERM_A, "ms99:x:y")));

        assertEquals(List.of("permisos"), fields(error));
        assertEquals("Permiso no válido: ms99:x:y", error.getErrors().get(0).mensaje());
    }

        /**
     * The catalog is defined by the database: a permission of any module that is in it is accepted
     */
    @Test
    void validateCreate_permissionPresentInTheCatalog_isAccepted() {
        CreateRoleCommand command = RoleRequestValidator.validateCreate(
                create("ROL", "Descripción", List.of("ms99:nuevo:permiso", PERM_A)),
                Set.of(PERM_A, "ms99:nuevo:permiso"));

        assertEquals(List.of(PERM_A, "ms99:nuevo:permiso"), command.permissions());
    }

        /**
     * The permission codes are not normalized: in uppercase they do not exist in the catalog
     */
    @Test
    void validateCreate_permissionCodeIsCaseSensitive() {
        InvalidRequestException error = invalidCreate(create("ROL", "Descripción", List.of("MS01:PROFILE:READ")));

        assertEquals("Permiso no válido: MS01:PROFILE:READ", error.getErrors().get(0).mensaje());
    }

        /**
     * The spaces around a permission code are trimmed
     */
    @Test
    void validateCreate_permissionCodeIsTrimmed() {
        CreateRoleCommand command = RoleRequestValidator.validateCreate(
                create("ROL", "Descripción", List.of("  " + PERM_A + " ")), CATALOG);

        assertEquals(List.of(PERM_A), command.permissions());
    }

        /**
     * A repeated permission is rejected with its code
     */
    @Test
    void validateCreate_repeatedPermission_isRejectedWithItsCode() {
        InvalidRequestException error = invalidCreate(create("ROL", "Descripción", List.of(PERM_A, PERM_B, PERM_A)));

        assertEquals("Permiso repetido: " + PERM_A, error.getErrors().get(0).mensaje());
    }

        /**
     * A code with characters that should not be returned to the client is not repeated in the message
     */
    @Test
    void validateCreate_unsafeCodeIsNotEchoed() {
        InvalidRequestException html = invalidCreate(create("ROL", "Descripción", List.of("<script>")));
        InvalidRequestException blank = invalidCreate(create("ROL", "Descripción", List.of("  ")));
        InvalidRequestException tooLong = invalidCreate(create("ROL", "Descripción", List.of("m".repeat(61))));

        assertEquals("Permiso no válido.", html.getErrors().get(0).mensaje());
        assertEquals("Permiso no válido.", blank.getErrors().get(0).mensaje());
        assertEquals("Permiso no válido.", tooLong.getErrors().get(0).mensaje());
    }

        /**
     * A null element inside the list is an invalid permission
     */
    @Test
    void validateCreate_nullPermissionInsideTheList_isRejected() {
        List<String> codes = new ArrayList<>();
        codes.add(null);

        InvalidRequestException error = invalidCreate(create("ROL", "Descripción", codes));

        assertEquals("Permiso no válido.", error.getErrors().get(0).mensaje());
    }

        /**
     * The errors of name, description and permissions are reported together
     */
    @Test
    void validateCreate_severalInvalidFields_reportsAllOfThem() {
        InvalidRequestException error = invalidCreate(create("1", "ab", List.of("ms99:x:y")));

        assertEquals(ValidationErrors.INVALID_DATA_MESSAGE, error.getMessage());
        assertEquals(List.of("descripcion", "permisos", "rol"), fields(error).stream().sorted().toList());
    }

        /**
     * A field that is not in the contract is rejected with an error for that field
     */
    @Test
    void validateCreate_unknownField_isRejectedPerField() {
        CreateRoleRequest request = create("ROL", "Descripción", List.of(PERM_A));
        request.registerUnknownField("es_sistema", true);

        InvalidRequestException error = invalidCreate(request);

        assertEquals(List.of("es_sistema"), fields(error));
        assertEquals("Campo no permitido.", error.getErrors().get(0).mensaje());
    }

    // ------------------------------------------------------------------ edition (F-11)

        /**
     * A correct edit normalizes the name and trims the description
     */
    @Test
    void validateUpdate_validRequest_returnsTheNormalizedCommand() {
        UpdateRoleCommand command = RoleRequestValidator.validateUpdate(update(" monitor ", "  Nueva descripción "));

        assertEquals("MONITOR", command.name());
        assertEquals("Nueva descripción", command.description());
    }

        /**
     * Renaming a role to DETAILS is rejected with the reserved name error in the rol field
     *
     * @param name reserved name
     */
    @ParameterizedTest
    @ValueSource(strings = {"DETAILS", "details", "Details"})
    void validateUpdate_reservedName_returnsTheReservedNameError(String name) {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validateUpdate(update(name, "Descripción")));

        assertEquals(List.of("rol"), fields(error));
        assertEquals(RoleRequestValidator.RESERVED_NAME_MESSAGE, error.getErrors().get(0).mensaje());
    }

        /**
     * An edit with an invalid name and description reports both fields
     */
    @Test
    void validateUpdate_invalidFields_reportsBoth() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validateUpdate(update("rol-malo", "<i>x</i>")));

        assertEquals(List.of("descripcion", "rol"), fields(error).stream().sorted().toList());
    }

        /**
     * An edit with an unknown field is rejected, the permisos field does not go in this body
     */
    @Test
    void validateUpdate_unknownField_isRejected() {
        UpdateRoleRequest request = update("ROL", "Descripción");
        request.registerUnknownField("permisos", List.of(PERM_A));

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validateUpdate(request));

        assertEquals(List.of("permisos"), fields(error));
        assertEquals("Campo no permitido.", error.getErrors().get(0).mensaje());
    }

    // ------------------------------------------------------------------ permissions (F-12)

        /**
     * A correct list is returned sorted
     */
    @Test
    void validatePermissions_validRequest_returnsTheSortedList() {
        RolePermissionsCommand command = RoleRequestValidator.validatePermissions(
                permissions(List.of(PERM_A, PERM_C)), CATALOG);

        assertEquals(List.of(PERM_C, PERM_A), command.permissions());
    }

    /**
     * An empty or missing list passes the validation: the service answers 422 if the mandatory permissions are
     * missing
     */
    @Test
    void validatePermissions_emptyOrMissing_isValidWithAnEmptyList() {
        RolePermissionsCommand empty = RoleRequestValidator.validatePermissions(permissions(List.of()), CATALOG);
        RolePermissionsCommand missing = RoleRequestValidator.validatePermissions(permissions(null), CATALOG);

        assertEquals(List.of(), empty.permissions());
        assertEquals(List.of(), missing.permissions());
    }

        /**
     * A repeated permission or one outside the catalog is rejected
     */
    @Test
    void validatePermissions_repeatedOrUnknown_isRejected() {
        InvalidRequestException repeated = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validatePermissions(permissions(List.of(PERM_A, PERM_A)), CATALOG));
        InvalidRequestException unknown = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validatePermissions(permissions(List.of("ms01:profile:delete")), CATALOG));

        assertEquals("Permiso repetido: " + PERM_A, repeated.getErrors().get(0).mensaje());
        assertEquals("Permiso no válido: ms01:profile:delete", unknown.getErrors().get(0).mensaje());
    }

        /**
     * An unknown field next to a valid list is rejected
     */
    @Test
    void validatePermissions_unknownField_isRejected() {
        UpdateRolePermissionsRequest request = permissions(List.of(PERM_A));
        request.registerUnknownField("rol", "ADMIN");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validatePermissions(request, CATALOG));

        assertEquals(List.of("rol"), fields(error));
    }

    // ------------------------------------------------------------------ {rol} path

        /**
     * The name of the path is trimmed and converted to uppercase
     */
    @Test
    void normalizePathRole_validName_isTrimmedAndUppercased() {
        assertEquals("MONITOR_EVACUACION", RoleRequestValidator.normalizePathRole(" monitor_evacuacion "));
        assertEquals("ADMIN", RoleRequestValidator.normalizePathRole("Admin"));
    }

        /**
     * An invalid name in the path answers 400 with the error in the rol field
     */
    @Test
    void normalizePathRole_invalidName_isRejected() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.normalizePathRole("rol-malo"));

        assertEquals(List.of("rol"), fields(error));
        assertEquals("Rol no válido.", error.getErrors().get(0).mensaje());
        assertTrue(error.getMessage().length() > 0);
    }

        /**
     * A null name in the path is also invalid
     */
    @Test
    void normalizePathRole_nullName_isRejected() {
        assertThrows(InvalidRequestException.class, () -> RoleRequestValidator.normalizePathRole(null));
    }
}
