package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

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
 * Validaciones de los formularios F-10, F-11 y F-12 (RF-MS01-10) y de la ruta {rol}: nombre normalizado,
 * descripción de 3 a 200 caracteres sin etiquetas, permisos del catálogo sin repetidos, campos desconocidos
 */
class RoleRequestValidatorTest {

    private static final String PERM_A = "ms04:checkin:write";
    private static final String PERM_B = "ms04:checkin:read_own";
    private static final String PERM_C = "ms01:profile:read";

    /**
     * Cuerpo de creación
     *
     * @param name nombre del rol
     * @param description descripción del rol
     * @param permissions permisos del rol
     * @return la petición
     */
    private CreateRoleRequest create(String name, String description, List<String> permissions) {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setRol(name);
        request.setDescripcion(description);
        request.setPermisos(permissions);
        return request;
    }

    /**
     * Cuerpo de edición
     *
     * @param name nombre del rol
     * @param description descripción del rol
     * @return la petición
     */
    private UpdateRoleRequest update(String name, String description) {
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setRol(name);
        request.setDescripcion(description);
        return request;
    }

    /**
     * Cuerpo de cambio de permisos
     *
     * @param permissions permisos del rol
     * @return la petición
     */
    private UpdateRolePermissionsRequest permissions(List<String> permissions) {
        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermisos(permissions);
        return request;
    }

    /**
     * Valida una creación que debe fallar
     *
     * @param request petición inválida
     * @return la excepción lanzada
     */
    private InvalidRequestException invalidCreate(CreateRoleRequest request) {
        return assertThrows(InvalidRequestException.class, () -> RoleRequestValidator.validateCreate(request));
    }

    /**
     * Campos con error de una excepción, en el orden en que llegaron
     *
     * @param error excepción de validación
     * @return los nombres de campo
     */
    private List<String> fields(InvalidRequestException error) {
        List<String> fields = new ArrayList<>();
        for (ErrorResponse.ErrorDetail detail : error.getErrors()) {
            fields.add(detail.campo());
        }
        return fields;
    }

    // ------------------------------------------------------------------ creación (F-10)

    /**
     * Una creación correcta se normaliza: nombre en mayúsculas, descripción recortada y permisos ordenados
     */
    @Test
    void validateCreate_validRequest_returnsTheNormalizedCommand() {
        CreateRoleCommand command = RoleRequestValidator.validateCreate(
                create("  monitor_evacuacion ", "  Apoya la evacuación  ", List.of(PERM_A, PERM_C, PERM_B)));

        assertEquals("MONITOR_EVACUACION", command.name());
        assertEquals("Apoya la evacuación", command.description());
        assertEquals(List.of(PERM_C, PERM_B, PERM_A), command.permissions());
    }

    /**
     * Los límites de longitud se aceptan: nombre de 2 y de 30 caracteres, descripción de 3 y de 200
     */
    @Test
    void validateCreate_lengthLimits_areAccepted() {
        CreateRoleCommand shortest = RoleRequestValidator.validateCreate(create("AB", "abc", List.of(PERM_A)));
        CreateRoleCommand longest = RoleRequestValidator.validateCreate(
                create("A".repeat(30), "d".repeat(200), List.of(PERM_A)));

        assertEquals("AB", shortest.name());
        assertEquals(30, longest.name().length());
        assertEquals(200, longest.description().length());
    }

    /**
     * Nombres que incumplen el formato de entrada: de 2 a 30 caracteres, letras sin tilde, números y guion
     * bajo, empezando con una letra
     *
     * @param name nombre inválido
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
     * DETAILS está reservado para la ruta GET /roles/details: en cualquier combinación de mayúsculas se rechaza
     * al crear con un error en el campo rol
     *
     * @param name nombre reservado
     */
    @ParameterizedTest
    @ValueSource(strings = {"DETAILS", "details", "Details", "  details  "})
    void validateCreate_reservedName_returnsTheReservedNameError(String name) {
        InvalidRequestException error = invalidCreate(create(name, "Descripción", List.of(PERM_A)));

        assertEquals(List.of("rol"), fields(error));
        assertEquals(RoleRequestValidator.RESERVED_NAME_MESSAGE, error.getErrors().get(0).mensaje());
    }

    /**
     * Solo el nombre exacto está reservado: los nombres que lo contienen se aceptan
     */
    @Test
    void validateCreate_namesContainingDetails_areAccepted() {
        assertEquals("DETAILS_X", RoleRequestValidator.validateCreate(
                create("details_x", "Descripción", List.of(PERM_A))).name());
        assertEquals("MY_DETAILS", RoleRequestValidator.validateCreate(
                create("my_details", "Descripción", List.of(PERM_A))).name());
        assertEquals("DETAIL", RoleRequestValidator.validateCreate(
                create("detail", "Descripción", List.of(PERM_A))).name());
    }

    /**
     * Un nombre ausente es un nombre inválido
     */
    @Test
    void validateCreate_nullName_returnsTheNameError() {
        InvalidRequestException error = invalidCreate(create(null, "Descripción", List.of(PERM_A)));

        assertEquals(List.of("rol"), fields(error));
    }

    /**
     * Descripciones fuera de las reglas: vacía, corta, larga o con etiquetas HTML (RG-03)
     *
     * @param description descripción inválida
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "ab", "  ab  ", "<b>negrita</b>", "texto > otro", "texto < otro"})
    void validateCreate_invalidDescription_returnsTheDescriptionError(String description) {
        InvalidRequestException error = invalidCreate(create("ROL", description, List.of(PERM_A)));

        assertEquals(List.of("descripcion"), fields(error));
        assertEquals(RoleRequestValidator.INVALID_DESCRIPTION_MESSAGE, error.getErrors().get(0).mensaje());
    }

    /**
     * Una descripción de más de 200 caracteres se rechaza, y una ausente también
     */
    @Test
    void validateCreate_tooLongOrMissingDescription_isRejected() {
        assertEquals(List.of("descripcion"),
                fields(invalidCreate(create("ROL", "d".repeat(201), List.of(PERM_A)))));
        assertEquals(List.of("descripcion"), fields(invalidCreate(create("ROL", null, List.of(PERM_A)))));
    }

    /**
     * Sin permisos, o con la lista vacía, se pide al menos uno
     */
    @Test
    void validateCreate_withoutPermissions_asksForAtLeastOne() {
        InvalidRequestException missing = invalidCreate(create("ROL", "Descripción", null));
        InvalidRequestException empty = invalidCreate(create("ROL", "Descripción", List.of()));

        assertEquals("Seleccione al menos un permiso.", missing.getErrors().get(0).mensaje());
        assertEquals("permisos", missing.getErrors().get(0).campo());
        assertEquals("Seleccione al menos un permiso.", empty.getErrors().get(0).mensaje());
    }

    /**
     * Un permiso fuera del catálogo se rechaza y su código se repite en el mensaje
     */
    @Test
    void validateCreate_permissionOutsideTheCatalog_isRejectedWithItsCode() {
        InvalidRequestException error = invalidCreate(create("ROL", "Descripción", List.of(PERM_A, "ms99:x:y")));

        assertEquals(List.of("permisos"), fields(error));
        assertEquals("Permiso no válido: ms99:x:y", error.getErrors().get(0).mensaje());
    }

    /**
     * Los códigos de permiso no se normalizan: en mayúsculas no existen en el catálogo
     */
    @Test
    void validateCreate_permissionCodeIsCaseSensitive() {
        InvalidRequestException error = invalidCreate(create("ROL", "Descripción", List.of("MS01:PROFILE:READ")));

        assertEquals("Permiso no válido: MS01:PROFILE:READ", error.getErrors().get(0).mensaje());
    }

    /**
     * Los espacios alrededor de un código de permiso se recortan
     */
    @Test
    void validateCreate_permissionCodeIsTrimmed() {
        CreateRoleCommand command = RoleRequestValidator.validateCreate(
                create("ROL", "Descripción", List.of("  " + PERM_A + " ")));

        assertEquals(List.of(PERM_A), command.permissions());
    }

    /**
     * Un permiso repetido se rechaza con su código
     */
    @Test
    void validateCreate_repeatedPermission_isRejectedWithItsCode() {
        InvalidRequestException error = invalidCreate(create("ROL", "Descripción", List.of(PERM_A, PERM_B, PERM_A)));

        assertEquals("Permiso repetido: " + PERM_A, error.getErrors().get(0).mensaje());
    }

    /**
     * Un código con caracteres que no conviene devolver al cliente no se repite en el mensaje
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
     * Un elemento nulo dentro de la lista es un permiso no válido
     */
    @Test
    void validateCreate_nullPermissionInsideTheList_isRejected() {
        List<String> codes = new ArrayList<>();
        codes.add(null);

        InvalidRequestException error = invalidCreate(create("ROL", "Descripción", codes));

        assertEquals("Permiso no válido.", error.getErrors().get(0).mensaje());
    }

    /**
     * Los errores de nombre, descripción y permisos se informan juntos
     */
    @Test
    void validateCreate_severalInvalidFields_reportsAllOfThem() {
        InvalidRequestException error = invalidCreate(create("1", "ab", List.of()));

        assertEquals(ValidationErrors.INVALID_DATA_MESSAGE, error.getMessage());
        assertEquals(List.of("descripcion", "permisos", "rol"), fields(error).stream().sorted().toList());
    }

    /**
     * Un campo que no está en el contrato se rechaza con un error por ese campo
     */
    @Test
    void validateCreate_unknownField_isRejectedPerField() {
        CreateRoleRequest request = create("ROL", "Descripción", List.of(PERM_A));
        request.registerUnknownField("es_sistema", true);

        InvalidRequestException error = invalidCreate(request);

        assertEquals(List.of("es_sistema"), fields(error));
        assertEquals("Campo no permitido.", error.getErrors().get(0).mensaje());
    }

    // ------------------------------------------------------------------ edición (F-11)

    /**
     * Una edición correcta normaliza el nombre y recorta la descripción
     */
    @Test
    void validateUpdate_validRequest_returnsTheNormalizedCommand() {
        UpdateRoleCommand command = RoleRequestValidator.validateUpdate(update(" monitor ", "  Nueva descripción "));

        assertEquals("MONITOR", command.name());
        assertEquals("Nueva descripción", command.description());
    }

    /**
     * Renombrar un rol a DETAILS se rechaza con el error de nombre reservado en el campo rol
     *
     * @param name nombre reservado
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
     * Una edición con nombre y descripción inválidos informa los dos campos
     */
    @Test
    void validateUpdate_invalidFields_reportsBoth() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validateUpdate(update("rol-malo", "<i>x</i>")));

        assertEquals(List.of("descripcion", "rol"), fields(error).stream().sorted().toList());
    }

    /**
     * Una edición con un campo desconocido se rechaza, el campo permisos no va en este cuerpo
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

    // ------------------------------------------------------------------ permisos (F-12)

    /**
     * Una lista correcta se devuelve ordenada
     */
    @Test
    void validatePermissions_validRequest_returnsTheSortedList() {
        RolePermissionsCommand command = RoleRequestValidator.validatePermissions(
                permissions(List.of(PERM_A, PERM_C)));

        assertEquals(List.of(PERM_C, PERM_A), command.permissions());
    }

    /**
     * Una lista vacía o ausente se rechaza: se pide al menos un permiso
     */
    @Test
    void validatePermissions_emptyOrMissing_isRejected() {
        InvalidRequestException empty = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validatePermissions(permissions(List.of())));
        InvalidRequestException missing = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validatePermissions(permissions(null)));

        assertEquals("Seleccione al menos un permiso.", empty.getErrors().get(0).mensaje());
        assertEquals("Seleccione al menos un permiso.", missing.getErrors().get(0).mensaje());
    }

    /**
     * Un permiso repetido o fuera del catálogo se rechaza
     */
    @Test
    void validatePermissions_repeatedOrUnknown_isRejected() {
        InvalidRequestException repeated = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validatePermissions(permissions(List.of(PERM_A, PERM_A))));
        InvalidRequestException unknown = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validatePermissions(permissions(List.of("ms01:profile:delete"))));

        assertEquals("Permiso repetido: " + PERM_A, repeated.getErrors().get(0).mensaje());
        assertEquals("Permiso no válido: ms01:profile:delete", unknown.getErrors().get(0).mensaje());
    }

    /**
     * Un campo desconocido junto a una lista válida se rechaza
     */
    @Test
    void validatePermissions_unknownField_isRejected() {
        UpdateRolePermissionsRequest request = permissions(List.of(PERM_A));
        request.registerUnknownField("rol", "ADMIN");

        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> RoleRequestValidator.validatePermissions(request));

        assertEquals(List.of("rol"), fields(error));
    }

    // ------------------------------------------------------------------ ruta {rol}

    /**
     * El nombre de la ruta se recorta y se pasa a mayúsculas
     */
    @Test
    void normalizePathRole_validName_isTrimmedAndUppercased() {
        assertEquals("MONITOR_EVACUACION", RoleRequestValidator.normalizePathRole(" monitor_evacuacion "));
        assertEquals("ADMIN", RoleRequestValidator.normalizePathRole("Admin"));
    }

    /**
     * Un nombre inválido en la ruta responde 400 con el error en el campo rol
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
     * Un nombre nulo en la ruta también es inválido
     */
    @Test
    void normalizePathRole_nullName_isRejected() {
        assertThrows(InvalidRequestException.class, () -> RoleRequestValidator.normalizePathRole(null));
    }
}
