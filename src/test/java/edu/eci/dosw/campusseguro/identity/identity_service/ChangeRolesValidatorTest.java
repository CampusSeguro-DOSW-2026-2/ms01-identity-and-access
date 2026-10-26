package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.service.ChangeRolesCommand;
import edu.eci.dosw.campusseguro.identity.identity_service.service.ChangeRolesValidator;

/**
 * Validaciones del formulario F-09 (PATCH /users/roles): ids, roles del catalogo, listas no vacias a la
 * vez y ningun rol en ambas listas. Todas responden 400 con el mensaje exacto del formulario
 */
class ChangeRolesValidatorTest {

    private static final String ID_ONE = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";
    private static final String ID_TWO = "7c2e1d3f-9e5b-4d2c-8b88-4a3f7e2b1c0d";

    /**
     * Crea una peticion con los valores indicados
     *
     * @param ids identificadores de usuario; puede ser null
     * @param add roles a agregar; puede ser null
     * @param remove roles a retirar; puede ser null
     * @return la peticion armada
     */
    private ChangeRolesRequest request(List<String> ids, List<String> add, List<String> remove) {
        ChangeRolesRequest request = new ChangeRolesRequest();
        request.setIds(ids);
        request.setAgregar(add);
        request.setRetirar(remove);
        return request;
    }

    /**
     * Valida una peticion que debe fallar y devuelve la excepcion
     *
     * @param request peticion invalida
     * @return la excepcion InvalidRequestException lanzada
     */
    private InvalidRequestException invalid(ChangeRolesRequest request) {
        return assertThrows(InvalidRequestException.class, () -> ChangeRolesValidator.validate(request));
    }

    /**
     * Una peticion correcta se convierte en un comando con tipos fuertes
     */
    @Test
    void validate_validRequest_returnsTheCommand() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of(ID_ONE, ID_TWO), List.of("PROFESOR", "BRIGADISTA"), List.of("ESTUDIANTE")));

        assertEquals(List.of(UUID.fromString(ID_ONE), UUID.fromString(ID_TWO)),
                new ArrayList<>(command.userIds()));
        assertEquals(EnumSet.of(UserRole.PROFESOR, UserRole.BRIGADISTA), command.rolesToAdd());
        assertEquals(EnumSet.of(UserRole.ESTUDIANTE), command.rolesToRemove());
    }

    /**
     * Solo agregar (retirar ausente) es valido
     */
    @Test
    void validate_onlyAdd_isValid() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of(ID_ONE), List.of("AUDITOR"), null));

        assertEquals(EnumSet.of(UserRole.AUDITOR), command.rolesToAdd());
        assertTrue(command.rolesToRemove().isEmpty());
    }

    /**
     * Solo retirar (agregar ausente) es valido
     */
    @Test
    void validate_onlyRemove_isValid() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of(ID_ONE), null, List.of("AUDITOR")));

        assertTrue(command.rolesToAdd().isEmpty());
        assertEquals(EnumSet.of(UserRole.AUDITOR), command.rolesToRemove());
    }

    /**
     * RG-01: los espacios alrededor de ids y roles se recortan antes de validar, y un id en mayusculas
     * se acepta
     */
    @Test
    void validate_trimsIdsAndRoles() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of("  " + ID_ONE.toUpperCase() + " "), List.of(" PROFESOR "), null));

        assertEquals(List.of(UUID.fromString(ID_ONE)), new ArrayList<>(command.userIds()));
        assertEquals(EnumSet.of(UserRole.PROFESOR), command.rolesToAdd());
    }

    /**
     * Un id repetido se procesa una sola vez, en el orden en que apareció por primera vez
     */
    @Test
    void validate_repeatedUserId_isProcessedOnce() {
        ChangeRolesCommand command = ChangeRolesValidator.validate(
                request(List.of(ID_ONE, ID_TWO, ID_ONE), List.of("PROFESOR"), null));

        assertEquals(List.of(UUID.fromString(ID_ONE), UUID.fromString(ID_TWO)),
                new ArrayList<>(command.userIds()));
    }

    /**
     * Sin ids: "Seleccione al menos un usuario." en el campo ids
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
     * Una lista de ids vacia cuenta como sin usuarios
     */
    @Test
    void validate_emptyIds_returnsFieldError() {
        InvalidRequestException error = invalid(request(List.of(), List.of("PROFESOR"), null));

        assertEquals("ids", error.getErrors().get(0).campo());
        assertEquals(ChangeRolesValidator.MISSING_USERS_MESSAGE, error.getErrors().get(0).mensaje());
    }

    /**
     * Un id que no es un UUID se rechaza en el campo ids
     */
    @Test
    void validate_idWithoutUuidFormat_returnsFieldError() {
        InvalidRequestException error = invalid(request(List.of(ID_ONE, "USR-1034"), List.of("PROFESOR"), null));

        assertEquals("ids", error.getErrors().get(0).campo());
        assertEquals(ChangeRolesValidator.INVALID_USER_ID_MESSAGE, error.getErrors().get(0).mensaje());
    }

    /**
     * Un id nulo dentro de la lista se rechaza en el campo ids
     */
    @Test
    void validate_nullIdInsideTheList_returnsFieldError() {
        List<String> ids = new ArrayList<>();
        ids.add(null);

        InvalidRequestException error = invalid(request(ids, List.of("PROFESOR"), null));

        assertEquals("ids", error.getErrors().get(0).campo());
    }

    /**
     * Un rol fuera del catalogo: "Seleccione roles válidos." en el campo agregar
     */
    @Test
    void validate_unknownRoleToAdd_returnsFieldError() {
        InvalidRequestException error = invalid(request(List.of(ID_ONE), List.of("SUPERUSUARIO"), null));

        assertEquals(1, error.getErrors().size());
        assertEquals("agregar", error.getErrors().get(0).campo());
        assertEquals("Seleccione roles válidos.", error.getErrors().get(0).mensaje());
    }

    /**
     * Un rol fuera del catalogo: "Seleccione roles válidos." en el campo retirar
     */
    @Test
    void validate_unknownRoleToRemove_returnsFieldError() {
        InvalidRequestException error = invalid(request(List.of(ID_ONE), null, List.of("profesor")));

        assertEquals("retirar", error.getErrors().get(0).campo());
        assertEquals(ChangeRolesValidator.INVALID_ROLES_MESSAGE, error.getErrors().get(0).mensaje());
    }

    /**
     * Un rol repetido dentro de la misma lista tambien es un rol no valido (uniqueItems)
     */
    @Test
    void validate_repeatedRoleInTheSameList_returnsFieldError() {
        InvalidRequestException error = invalid(
                request(List.of(ID_ONE), List.of("PROFESOR", "PROFESOR"), null));

        assertEquals("agregar", error.getErrors().get(0).campo());
        assertEquals(ChangeRolesValidator.INVALID_ROLES_MESSAGE, error.getErrors().get(0).mensaje());
    }

    /**
     * Se reportan juntos los errores de varios campos
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
     * Un rol en ambas listas: "Un rol no puede agregarse y retirarse a la vez."
     */
    @Test
    void validate_sameRoleInBothLists_returnsTheMessage() {
        InvalidRequestException error = invalid(
                request(List.of(ID_ONE), List.of("PROFESOR", "AUDITOR"), List.of("ESTUDIANTE", "AUDITOR")));

        assertEquals("Un rol no puede agregarse y retirarse a la vez.", error.getMessage());
        assertTrue(error.getErrors().isEmpty());
    }

    /**
     * Los errores de formato tienen prioridad sobre las reglas que cruzan las dos listas
     */
    @Test
    void validate_fieldErrorsTakePriorityOverCrossListRules() {
        InvalidRequestException error = invalid(request(List.of("no-es-uuid"), List.of(), List.of()));

        assertEquals(ChangeRolesValidator.INVALID_DATA_MESSAGE, error.getMessage());
        assertEquals("ids", error.getErrors().get(0).campo());
    }

    /**
     * Los ocho roles del catalogo se pueden agregar en una sola peticion (maximo de la lista)
     */
    @Test
    void validate_allEightRoles_areAccepted() {
        List<String> everyRole = new ArrayList<>();
        for (UserRole role : UserRole.values()) {
            everyRole.add(role.name());
        }

        ChangeRolesCommand command = ChangeRolesValidator.validate(request(List.of(ID_ONE), everyRole, null));

        assertEquals(EnumSet.allOf(UserRole.class), command.rolesToAdd());
    }

    /**
     * Un campo que no está en el contrato se rechaza con un error por ese campo (RG-04)
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
     * Los campos desconocidos se informan junto con los demás errores del formulario
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
