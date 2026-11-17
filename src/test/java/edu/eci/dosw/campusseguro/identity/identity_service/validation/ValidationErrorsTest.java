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
 * Conversion of the validation violations into the detail by field of the ErrorResponse, one error per field, with the
 * name of the contract field and in a stable order
 */
class ValidationErrorsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

        /**
     * Creates the validator that is shared between the tests
     */
    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

        /**
     * Releases the validator factory
     */
    @AfterAll
    static void close() {
        factory.close();
    }

        /**
     * The union keeps the first message of each field and sorts by name
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
     * The union of an empty list is empty
     */
    @Test
    void merge_ofAnEmptyList_isEmpty() {
        assertTrue(ValidationErrors.merge(List.of()).isEmpty());
    }

        /**
     * The name of the contract is taken from the JSON property of the field
     */
    @Test
    void wireName_usesTheJsonPropertyOfTheField() {
        assertEquals("primer_nombre", ValidationErrors.wireName(UserCreateRequest.class, "firstName"));
        assertEquals("confirmar_contrasena",
                ValidationErrors.wireName(UserCreateRequest.class, "passwordConfirmation"));
    }

        /**
     * The path of a collection element is reduced to its first segment
     */
    @Test
    void wireName_ofAContainerElementPath_isTheFirstSegment() {
        assertEquals("ids", ValidationErrors.wireName(ChangeStatusRequest.class, "ids[0]"));
        assertEquals("ids", ValidationErrors.wireName(ChangeStatusRequest.class, "ids[0].valor"));
        assertEquals("ids", ValidationErrors.wireName(ChangeStatusRequest.class, "ids.valor"));
    }

        /**
     * An unknown property is returned as it is
     */
    @Test
    void wireName_ofAnUnknownProperty_isReturnedAsIs() {
        assertEquals("otra", ValidationErrors.wireName(UserCreateRequest.class, "otra"));
    }

        /**
     * The conversion of violations uses the names of the contract and sorts them
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
     * The conversion of field errors uses the names of the contract and keeps the first message
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
