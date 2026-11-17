package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.ChangeRolesCommand;
import edu.eci.dosw.campusseguro.identity.identity_service.service.ChangeRolesValidator;

/**
 * Validations of form F-09 (PATCH /users/roles): ids, roles of the catalog, lists that are not both empty and no role
 * in both lists. All of them answer 400 with the exact message of the form
 */
class ChangeRolesValidatorTest {

    private static final String ID_ONE = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";
    private static final String ID_TWO = "7c2e1d3f-9e5b-4d2c-8b88-4a3f7e2b1c0d";

        /**
     * Creates a request with the given values
     *
     * @param ids user identifiers; may be null
     * @param add roles to add; may be null
     * @param remove roles to remove; may be null
     * @return the assembled request
     */
    private ChangeRolesRequest request(List<String> ids, List<String> add, List<String> remove) {
        ChangeRolesRequest request = new ChangeRolesRequest();
        request.setIds(ids);
        request.setAgregar(add);
        request.setRetirar(remove);
        return request;
    }

        /**
     * Validates a request that must fail and returns the exception
     *
     * @param request invalid request
     * @return the InvalidRequestException thrown
     */
    private InvalidRequestException invalid(ChangeRolesRequest request) {
        return assertThrows(InvalidRequestException.class, () -> ChangeRolesValidator.validate(request));
    }

        /**
     * A correct request is converted into a command with strong types
     */
    @Test
    void validate_validRequest_returnsTheCommand() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of(ID_ONE, ID_TWO), List.of("PROFESOR", "BRIGADISTA"), List.of("ESTUDIANTE")));

        assertEquals(List.of(UUID.fromString(ID_ONE), UUID.fromString(ID_TWO)),
                new ArrayList<>(command.userIds()));
        assertEquals(Set.of("PROFESOR", "BRIGADISTA"), command.rolesToAdd());
        assertEquals(Set.of("ESTUDIANTE"), command.rolesToRemove());
    }

        /**
     * Only adding (removing absent) is valid
     */
    @Test
    void validate_onlyAdd_isValid() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of(ID_ONE), List.of("AUDITOR"), null));

        assertEquals(Set.of("AUDITOR"), command.rolesToAdd());
        assertTrue(command.rolesToRemove().isEmpty());
    }

        /**
     * Only removing (adding absent) is valid
     */
    @Test
    void validate_onlyRemove_isValid() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of(ID_ONE), null, List.of("AUDITOR")));

        assertTrue(command.rolesToAdd().isEmpty());
        assertEquals(Set.of("AUDITOR"), command.rolesToRemove());
    }

        /**
     * RG-01: the spaces around ids and roles are trimmed before validating, and an uppercase id is accepted
     */
    @Test
    void validate_trimsIdsAndRoles() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of("  " + ID_ONE.toUpperCase() + " "), List.of(" PROFESOR "), null));

        assertEquals(List.of(UUID.fromString(ID_ONE)), new ArrayList<>(command.userIds()));
        assertEquals(Set.of("PROFESOR"), command.rolesToAdd());
    }

        /**
     * A repeated id is processed only once, in the order in which it first appeared
     */
    @Test
    void validate_repeatedUserId_isProcessedOnce() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of(ID_ONE, ID_TWO, ID_ONE), List.of("PROFESOR"), null));

        assertEquals(List.of(UUID.fromString(ID_ONE), UUID.fromString(ID_TWO)),
                new ArrayList<>(command.userIds()));
    }

        /**
     * Without ids: "Seleccione al menos un usuario." in the ids field
     */
    @Test
    void validate_missingIds_returnsFieldError() {
        InvalidRequestException error = invalid(request(null, List.of("PROFESOR"), null));

        assertEquals(ChangeRolesValidator.INVALID_DATA_MESSAGE, error.getMessage());
        assertEquals(1, error.getErrors().size());
        ErrorResponse.ErrorDetail detail = error.getErrors().get(0);
        assertEquals("ids", detail.campo());
        assertEquals("Seleccione al menos un usuario.", detail.mensaje());
    }

        /**
     * An empty list of ids counts as no users
     */
    @Test
    void validate_emptyIds_returnsFieldError() {
        InvalidRequestException error = invalid(request(List.of(), List.of("PROFESOR"), null));

        assertEquals("ids", error.getErrors().get(0).campo());
        assertEquals(ChangeRolesValidator.MISSING_USERS_MESSAGE, error.getErrors().get(0).mensaje());
    }

        /**
     * An id that is not a UUID is rejected in the ids field
     */
    @Test
    void validate_idWithoutUuidFormat_returnsFieldError() {
        InvalidRequestException error = invalid(request(List.of(ID_ONE, "USR-1034"), List.of("PROFESOR"), null));

        assertEquals("ids", error.getErrors().get(0).campo());
        assertEquals(ChangeRolesValidator.INVALID_USER_ID_MESSAGE, error.getErrors().get(0).mensaje());
    }

        /**
     * A null id inside the list is rejected in the ids field
     */
    @Test
    void validate_nullIdInsideTheList_returnsFieldError() {
        List<String> ids = new ArrayList<>();
        ids.add(null);

        InvalidRequestException error = invalid(request(ids, List.of("PROFESOR"), null));

        assertEquals("ids", error.getErrors().get(0).campo());
    }

        /**
     * A role name without the stored format: "Seleccione roles válidos." in the agregar field, whether the role exists
     * is checked by the service
     */
    @Test
    void validate_roleWithoutTheStoredFormatToAdd_returnsFieldError() {
        InvalidRequestException error = invalid(request(List.of(ID_ONE), List.of("SUPER-USUARIO"), null));

        assertEquals(1, error.getErrors().size());
        assertEquals("agregar", error.getErrors().get(0).campo());
        assertEquals("Seleccione roles válidos.", error.getErrors().get(0).mensaje());
    }

        /**
     * A role outside the catalog: "Seleccione roles válidos." in the retirar field
     */
    @Test
    void validate_unknownRoleToRemove_returnsFieldError() {
        InvalidRequestException error = invalid(request(List.of(ID_ONE), null, List.of("profesor")));

        assertEquals("retirar", error.getErrors().get(0).campo());
        assertEquals(ChangeRolesValidator.INVALID_ROLES_MESSAGE, error.getErrors().get(0).mensaje());
    }

        /**
     * A role repeated inside the same list is also an invalid role (uniqueItems)
     */
    @Test
    void validate_repeatedRoleInTheSameList_returnsFieldError() {
        InvalidRequestException error = invalid(
                request(List.of(ID_ONE), List.of("PROFESOR", "PROFESOR"), null));

        assertEquals("agregar", error.getErrors().get(0).campo());
        assertEquals(ChangeRolesValidator.INVALID_ROLES_MESSAGE, error.getErrors().get(0).mensaje());
    }

        /**
     * The errors of several fields are reported together
     */
    @Test
    void validate_severalInvalidFields_reportsAllOfThem() {
        InvalidRequestException error = invalid(request(List.of(), List.of("X"), List.of("Y")));

        List<String> fields = new ArrayList<>();
        for (ErrorResponse.ErrorDetail detail : error.getErrors()) {
            fields.add(detail.campo());
        }
        assertEquals(List.of("agregar", "ids", "retirar"), fields);
    }

    /**
     * Ambas listas ausentes: "Indique roles a agregar o retirar."
     */
    @Test
    void validate_bothListsMissing_returnsTheMessage() {
        InvalidRequestException error = invalid(request(List.of(ID_ONE), null, null));

        assertEquals("Indique roles a agregar o retirar.", error.getMessage());
        assertTrue(error.getErrors().isEmpty());
    }

    /**
     * Ambas listas vacias: "Indique roles a agregar o retirar."
     */
    @Test
    void validate_bothListsEmpty_returnsTheMessage() {
        InvalidRequestException error = invalid(request(List.of(ID_ONE), List.of(), List.of()));

        assertEquals(ChangeRolesValidator.NO_ROLES_GIVEN_MESSAGE, error.getMessage());
    }

        /**
     * A role in both lists: "Un rol no puede agregarse y retirarse a la vez."
     */
    @Test
    void validate_sameRoleInBothLists_returnsTheMessage() {
        InvalidRequestException error = invalid(
                request(List.of(ID_ONE), List.of("PROFESOR", "AUDITOR"), List.of("ESTUDIANTE", "AUDITOR")));

        assertEquals("Un rol no puede agregarse y retirarse a la vez.", error.getMessage());
        assertTrue(error.getErrors().isEmpty());
    }

        /**
     * The format errors take priority over the rules that cross the two lists
     */
    @Test
    void validate_fieldErrorsTakePriorityOverCrossListRules() {
        InvalidRequestException error = invalid(request(List.of("no-es-uuid"), List.of(), List.of()));

        assertEquals(ChangeRolesValidator.INVALID_DATA_MESSAGE, error.getMessage());
        assertEquals("ids", error.getErrors().get(0).campo());
    }

        /**
     * All the roles of the initial catalog can be added in a single request
     */
    @Test
    void validate_allSeedRoles_areAccepted() {
        List<String> everyRole = new ArrayList<>();
        for (String role : SeedRoles.PERMISSIONS.keySet()) {
            everyRole.add(role);
        }

        ChangeRolesCommand command = ChangeRolesValidator.validate(request(List.of(ID_ONE), everyRole, null));

        assertEquals(SeedRoles.PERMISSIONS.keySet(), command.rolesToAdd());
    }

        /**
     * The agregar and retirar lists have no size limit per operation: 60 roles in each one are accepted (the limit of
     * 50 roles per user is verified in the service, against the roles that it already has)
     */
    @Test
    void validate_listsWithManyRoles_areAccepted() {
        List<String> toAdd = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            toAdd.add(String.format("AGREGAR_%02d", i));
            toRemove.add(String.format("RETIRAR_%02d", i));
        }

        ChangeRolesCommand command = ChangeRolesValidator.validate(request(List.of(ID_ONE), toAdd, toRemove));

        assertEquals(60, command.rolesToAdd().size());
        assertEquals(60, command.rolesToRemove().size());
    }

        /**
     * A field that is not in the contract is rejected with an error for that field (RG-04)
     */
    @Test
    void validate_unknownFieldInTheBody_isRejectedPerField() {
        ChangeRolesRequest request = request(List.of(ID_ONE), List.of("PROFESOR"), null);
        request.registerUnknownField("solicitante", ID_TWO);

        InvalidRequestException error = invalid(request);

        assertEquals(1, error.getErrors().size());
        assertEquals("solicitante", error.getErrors().get(0).campo());
        assertEquals("El campo no está permitido.", error.getErrors().get(0).mensaje());
    }

        /**
     * The unknown fields are reported together with the other errors of the form
     */
    @Test
    void validate_unknownFieldAndInvalidFields_reportsAllOfThem() {
        ChangeRolesRequest request = request(List.of(), List.of("X"), null);
        request.registerUnknownField("rol", "ADMIN");

        InvalidRequestException error = invalid(request);

        List<String> fields = new ArrayList<>();
        for (ErrorResponse.ErrorDetail detail : error.getErrors()) {
            fields.add(detail.campo());
        }
        assertTrue(fields.contains("rol"));
        assertTrue(fields.contains("ids"));
        assertTrue(fields.contains("agregar"));
    }
}
