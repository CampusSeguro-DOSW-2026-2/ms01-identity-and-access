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
 * Reglas del formulario F-08 de cambio de estado
 */
class ChangeStatusRequestValidationTest {

    private static final String ID_ONE = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";
    private static final String ID_TWO = "7c2e1d3f-9e5b-4d2c-8b88-4a3f7e2b1c0d";

    private static ValidatorFactory factory;
    private static RequestValidator validator;

    /**
     * Crea el validador que se comparte entre las pruebas
     */
    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = new RequestValidator(factory.getValidator());
    }

    /**
     * Libera la fábrica de validadores
     */
    @AfterAll
    static void close() {
        factory.close();
    }

    /**
     * Construye una petición de cambio de estado
     *
     * @param ids identificadores de los usuarios
     * @param estado estado solicitado
     * @return la petición construida
     */
    private static ChangeStatusRequest request(List<String> ids, String estado) {
        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setIds(ids);
        request.setEstado(estado);
        return request;
    }

    /**
     * Obtiene el mensaje de error de cada campo inválido
     *
     * @param request petición que se valida
     * @return mapa de nombre de campo a mensaje, ordenado por campo
     */
    private static Map<String, String> errors(ChangeStatusRequest request) {
        Map<String, String> result = new TreeMap<>();
        for (ErrorResponse.ErrorDetail error : validator.collectErrors(request)) {
            result.put(error.campo(), error.mensaje());
        }
        return result;
    }

    /**
     * Una petición válida no produce errores
     */
    @Test
    void validRequest_hasNoErrors() {
        assertTrue(errors(request(List.of(ID_ONE, ID_TWO), "INACTIVO")).isEmpty());
        assertTrue(errors(request(List.of(ID_ONE), "ACTIVO")).isEmpty());
    }

    /**
     * Una lista de identificadores vacía o ausente se rechaza con el mensaje del formulario
     */
    @Test
    void emptyOrMissingIds_areRejectedWithTheFormMessage() {
        assertEquals(Map.of("ids", "Seleccione al menos un usuario."), errors(request(List.of(), "ACTIVO")));
        assertEquals(Map.of("ids", "Seleccione al menos un usuario."), errors(request(null, "ACTIVO")));
    }

    /**
     * Un identificador inválido se informa sobre el campo de identificadores
     */
    @Test
    void invalidIdInTheList_isReportedOnTheIdsField() {
        assertEquals(Map.of("ids", "Identificador de usuario no válido."),
                errors(request(List.of(ID_ONE, "no-es-uuid"), "ACTIVO")));
    }

    /**
     * Un identificador nulo o en blanco se rechaza
     */
    @Test
    void nullOrBlankIdInTheList_isRejected() {
        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertEquals(Map.of("ids", "Identificador de usuario no válido."), errors(request(withNull, "ACTIVO")));
        assertEquals(Map.of("ids", "Identificador de usuario no válido."), errors(request(List.of("  "), "ACTIVO")));
    }

    /**
     * Un estado inválido o ausente se rechaza con el mensaje del formulario
     */
    @Test
    void invalidOrMissingState_isRejectedWithTheFormMessage() {
        assertEquals(Map.of("estado", "Estado no válido."), errors(request(List.of(ID_ONE), "PENDIENTE")));
        assertEquals(Map.of("estado", "Estado no válido."), errors(request(List.of(ID_ONE), null)));
        assertEquals(Map.of("estado", "Estado no válido."), errors(request(List.of(ID_ONE), "activo")));
    }

    /**
     * Un campo que no está en el contrato se rechaza (RG-04)
     */
    @Test
    void unknownField_isRejected() {
        ChangeStatusRequest request = request(List.of(ID_ONE), "ACTIVO");
        request.registerUnknownField("rol", "ADMIN");

        assertEquals(Map.of("rol", "El campo no está permitido."), errors(request));
    }

    /**
     * Los campos de texto se recortan (RG-01)
     */
    @Test
    void textFields_areTrimmed() {
        ChangeStatusRequest request = request(List.of("  " + ID_ONE + " "), "  ACTIVO ");

        assertEquals(List.of(ID_ONE), request.getIds());
        assertEquals("ACTIVO", request.getEstado());
        assertTrue(errors(request).isEmpty());
    }

    /**
     * La conversión a identificadores conserva el orden de la petición y elimina repetidos
     */
    @Test
    void toUserIds_keepsTheRequestOrderAndRemovesDuplicates() {
        ChangeStatusRequest request = request(List.of(ID_TWO, ID_ONE, ID_TWO), "ACTIVO");

        Set<UUID> ids = request.toUserIds();

        assertEquals(List.of(UUID.fromString(ID_TWO), UUID.fromString(ID_ONE)), List.copyOf(ids));
    }

    /**
     * La conversión del estado devuelve el valor del catálogo
     */
    @Test
    void toState_returnsTheCatalogValue() {
        assertEquals(UserState.INACTIVO, request(List.of(ID_ONE), "INACTIVO").toState());
        assertNull(request(List.of(ID_ONE), "  ").getEstado());
    }
}
