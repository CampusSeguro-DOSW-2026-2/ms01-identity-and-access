package edu.eci.dosw.campusseguro.identity.identity_service.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.validation.FieldError;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Conversión de las violaciones de validación al detalle por campo del ErrorResponse, un error por campo,
 * con el nombre del campo del contrato y en orden estable
 */
class ValidationErrorsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    /**
     * Crea el validador que se comparte entre las pruebas
     */
    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Libera la fábrica de validadores
     */
    @AfterAll
    static void close() {
        factory.close();
    }

    /**
     * La unión conserva el primer mensaje de cada campo y ordena por nombre
     */
    @Test
    void merge_keepsTheFirstMessagePerFieldSortedByName() {
        List<ErrorResponse.ErrorDetail> merged = ValidationErrors.merge(List.of(
                new ErrorResponse.ErrorDetail("rol", "segundo"),
                new ErrorResponse.ErrorDetail("correo", "primero"),
                new ErrorResponse.ErrorDetail("rol", "repetido")));

        assertEquals(List.of(
                new ErrorResponse.ErrorDetail("correo", "primero"),
                new ErrorResponse.ErrorDetail("rol", "segundo")), merged);
    }

    /**
     * La unión de una lista vacía es vacía
     */
    @Test
    void merge_ofAnEmptyList_isEmpty() {
        assertTrue(ValidationErrors.merge(List.of()).isEmpty());
    }

    /**
     * El nombre del contrato se toma de la propiedad JSON del campo
     */
    @Test
    void wireName_usesTheJsonPropertyOfTheField() {
        assertEquals("primer_nombre", ValidationErrors.wireName(UserCreateRequest.class, "firstName"));
        assertEquals("confirmar_contrasena",
                ValidationErrors.wireName(UserCreateRequest.class, "passwordConfirmation"));
    }

    /**
     * La ruta de un elemento de colección se reduce a su primer tramo
     */
    @Test
    void wireName_ofAContainerElementPath_isTheFirstSegment() {
        assertEquals("ids", ValidationErrors.wireName(ChangeStatusRequest.class, "ids[0]"));
        assertEquals("ids", ValidationErrors.wireName(ChangeStatusRequest.class, "ids[0].valor"));
        assertEquals("ids", ValidationErrors.wireName(ChangeStatusRequest.class, "ids.valor"));
    }

    /**
     * Una propiedad desconocida se devuelve tal cual
     */
    @Test
    void wireName_ofAnUnknownProperty_isReturnedAsIs() {
        assertEquals("otra", ValidationErrors.wireName(UserCreateRequest.class, "otra"));
    }

    /**
     * La conversión de violaciones usa los nombres del contrato y los ordena
     */
    @Test
    void fromViolations_usesTheContractNamesAndSortsThem() {
        UserCreateRequest request = new UserCreateRequest();
        request.setFirstName("A");
        request.setEmail("x");

        Set<ConstraintViolation<UserCreateRequest>> violations = validator.validate(request);
        List<ErrorResponse.ErrorDetail> errors = ValidationErrors.fromViolations(UserCreateRequest.class,
                violations);

        List<String> fields = errors.stream()
                .map(ErrorResponse.ErrorDetail::campo)
                .toList();
        assertTrue(fields.contains("primer_nombre"));
        assertTrue(fields.contains("correo"));
        assertTrue(fields.stream().noneMatch(field -> "firstName".equals(field)));
        assertEquals(fields.stream().sorted().toList(), fields);
        assertEquals(fields.size(), fields.stream().distinct().count());
    }

    /**
     * La conversión de errores de campo usa los nombres del contrato y conserva el primer mensaje
     */
    @Test
    void fromFieldErrors_usesTheContractNamesAndKeepsTheFirstMessage() {
        List<FieldError> fieldErrors = List.of(
                new FieldError("userCreateRequest", "passwordConfirmation", "primer mensaje"),
                new FieldError("userCreateRequest", "passwordConfirmation", "segundo mensaje"),
                new FieldError("userCreateRequest", "email", "Correo no válido."));

        List<ErrorResponse.ErrorDetail> errors = ValidationErrors.fromFieldErrors(UserCreateRequest.class,
                fieldErrors);

        assertEquals(List.of(
                new ErrorResponse.ErrorDetail("confirmar_contrasena", "primer mensaje"),
                new ErrorResponse.ErrorDetail("correo", "Correo no válido.")), errors);
    }
}
