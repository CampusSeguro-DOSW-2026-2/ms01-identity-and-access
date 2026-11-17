package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/**
 * Rules of form F-08 for the status change
 */
class ChangeStatusRequestValidationTest {

    private static final String ID_ONE = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";
    private static final String ID_TWO = "7c2e1d3f-9e5b-4d2c-8b88-4a3f7e2b1c0d";

    private static ValidatorFactory factory;
    private static RequestValidator validator;

        /**
     * Creates the validator that is shared between the tests
     */
    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = new RequestValidator(factory.getValidator());
    }

        /**
     * Releases the validator factory
     */
    @AfterAll
    static void close() {
        factory.close();
    }

        /**
     * Builds a status change request
     *
     * @param ids identifiers of the users
     * @param estado requested status
     * @return the built request
     */
    private static ChangeStatusRequest request(List<String> ids, String estado) {
        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setIds(ids);
        request.setEstado(estado);
        return request;
    }

        /**
     * Gets the error message of each invalid field
     *
     * @param request request that is validated
     * @return map from field name to message, sorted by field
     */
    private static Map<String, String> errors(ChangeStatusRequest request) {
        Map<String, String> result = new TreeMap<>();
        for (ErrorResponse.ErrorDetail error : validator.collectErrors(request)) {
            result.put(error.campo(), error.mensaje());
        }
        return result;
    }

        /**
     * A valid request produces no errors
     */
    @Test
    void validRequest_hasNoErrors() {
        assertTrue(errors(request(List.of(ID_ONE, ID_TWO), "INACTIVO")).isEmpty());
        assertTrue(errors(request(List.of(ID_ONE), "ACTIVO")).isEmpty());
    }

        /**
     * An empty or absent list of identifiers is rejected with the message of the form
     */
    @Test
    void emptyOrMissingIds_areRejectedWithTheFormMessage() {
        assertEquals(Map.of("ids", "Seleccione al menos un usuario."), errors(request(List.of(), "ACTIVO")));
        assertEquals(Map.of("ids", "Seleccione al menos un usuario."), errors(request(null, "ACTIVO")));
    }

        /**
     * An invalid identifier is reported on the identifiers field
     */
    @Test
    void invalidIdInTheList_isReportedOnTheIdsField() {
        assertEquals(Map.of("ids", "Identificador de usuario no válido."),
                errors(request(List.of(ID_ONE, "no-es-uuid"), "ACTIVO")));
    }

        /**
     * A null or blank identifier is rejected
     */
    @Test
    void nullOrBlankIdInTheList_isRejected() {
        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertEquals(Map.of("ids", "Identificador de usuario no válido."), errors(request(withNull, "ACTIVO")));
        assertEquals(Map.of("ids", "Identificador de usuario no válido."), errors(request(List.of("  "), "ACTIVO")));
    }

        /**
     * An invalid or absent status is rejected with the message of the form
     */
    @Test
    void invalidOrMissingState_isRejectedWithTheFormMessage() {
        assertEquals(Map.of("estado", "Estado no válido."), errors(request(List.of(ID_ONE), "PENDIENTE")));
        assertEquals(Map.of("estado", "Estado no válido."), errors(request(List.of(ID_ONE), null)));
        assertEquals(Map.of("estado", "Estado no válido."), errors(request(List.of(ID_ONE), "activo")));
    }

        /**
     * A field that is not in the contract is rejected (RG-04)
     */
    @Test
    void unknownField_isRejected() {
        ChangeStatusRequest request = request(List.of(ID_ONE), "ACTIVO");
        request.registerUnknownField("rol", "ADMIN");

        assertEquals(Map.of("rol", "El campo no está permitido."), errors(request));
    }

        /**
     * The text fields are trimmed (RG-01)
     */
    @Test
    void textFields_areTrimmed() {
        ChangeStatusRequest request = request(List.of("  " + ID_ONE + " "), "  ACTIVO ");

        assertEquals(List.of(ID_ONE), request.getIds());
        assertEquals("ACTIVO", request.getEstado());
        assertTrue(errors(request).isEmpty());
    }

        /**
     * The conversion to identifiers keeps the order of the request and removes repeated ones
     */
    @Test
    void toUserIds_keepsTheRequestOrderAndRemovesDuplicates() {
        ChangeStatusRequest request = request(List.of(ID_TWO, ID_ONE, ID_TWO), "ACTIVO");

        Set<UUID> ids = request.toUserIds();

        assertEquals(List.of(UUID.fromString(ID_TWO), UUID.fromString(ID_ONE)), List.copyOf(ids));
    }

        /**
     * The conversion of the status returns the value of the catalog
     */
    @Test
    void toState_returnsTheCatalogValue() {
        assertEquals(UserState.INACTIVO, request(List.of(ID_ONE), "INACTIVO").toState());
        assertNull(request(List.of(ID_ONE), "  ").getEstado());
    }
}
