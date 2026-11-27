package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/**
 * Rules of registration form F-01 and of the general rules RG-01, RG-02 and RG-04
 */
class UserCreateRequestValidationTest {

    private static final String PASSWORD = "MiPassword#2026";
    private static final String PASSWORD_MESSAGE =
            "La contraseña debe tener 8 a 64 caracteres, con mayúscula, minúscula, número y carácter especial.";

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
     * Provides first names that break the rules
     *
     * @return the invalid values
     */
    private static Stream<String> invalidFirstNames() {
        return Stream.of("A", "Ana123", "Ana#", "a".repeat(31));
    }

        /**
     * Provides first names that meet the rules
     *
     * @return the valid values
     */
    private static Stream<String> validFirstNames() {
        return Stream.of("Ana", "Jean-Luc", "D'Artagnan", "De la Cruz", "Ñandú", "ab", "a".repeat(30));
    }

        /**
     * Builds a valid registration request
     *
     * @return the valid request
     */
    private static UserCreateRequest valid() {
        UserCreateRequest request = new UserCreateRequest();
        request.setFirstName("María");
        request.setSecondName("José");
        request.setFirstLastName("Pérez");
        request.setSecondLastName("Núñez");
        request.setEmail("maria.perez@escuelaing.edu.co");
        request.setPassword(PASSWORD);
        request.setPasswordConfirmation(PASSWORD);
        return request;
    }

        /**
     * Gets the error message of each invalid field
     *
     * @param request request that is validated
     * @return map from field name to message, sorted by field
     */
    private static Map<String, String> errors(UserCreateRequest request) {
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
        assertTrue(errors(valid()).isEmpty());
    }

        /**
     * The optional names can be omitted
     */
    @Test
    void optionalNamesMayBeOmitted() {
        UserCreateRequest request = valid();
        request.setSecondName(null);
        request.setSecondLastName(null);

        assertTrue(errors(request).isEmpty());
    }

        /**
     * The blank optional names are treated as absent (RG-02)
     */
    @Test
    void blankOptionalNames_areTreatedAsAbsent_RG02() {
        UserCreateRequest request = valid();
        request.setSecondName("   ");
        request.setSecondLastName("");

        assertNull(request.getSecondName());
        assertNull(request.getSecondLastName());
        assertTrue(errors(request).isEmpty());
    }

        /**
     * The text fields are trimmed (RG-01)
     */
    @Test
    void textFields_areTrimmed_RG01() {
        UserCreateRequest request = valid();
        request.setFirstName("  Ana  ");

        assertEquals("Ana", request.getFirstName());
    }

        /**
     * The email is trimmed and converted to lowercase (RG-01)
     */
    @Test
    void email_isTrimmedAndLowercased_RG01() {
        UserCreateRequest request = valid();
        request.setEmail("  Maria.Perez@EscuelaIng.edu.co ");

        assertEquals("maria.perez@escuelaing.edu.co", request.getEmail());
        assertTrue(errors(request).isEmpty());
    }

        /**
     * The password is never trimmed (RG-01)
     */
    @Test
    void password_isNeverTrimmed_RG01() {
        UserCreateRequest request = valid();
        request.setPassword(" " + PASSWORD + " ");

        assertEquals(" " + PASSWORD + " ", request.getPassword());
    }

        /**
     * The absent required fields produce one message per field
     */
    @Test
    void missingRequiredFields_reportOneMessagePerField() {
        Map<String, String> result = errors(new UserCreateRequest());

        assertEquals("El primer nombre debe tener entre 2 y 30 letras.", result.get("primer_nombre"));
        assertEquals("El primer apellido debe tener entre 2 y 30 letras.", result.get("primer_apellido"));
        assertEquals("Correo no válido.", result.get("correo"));
        assertEquals(PASSWORD_MESSAGE, result.get("contrasena"));
        assertEquals("Las contraseñas no coinciden.", result.get("confirmar_contrasena"));
        assertEquals(5, result.size());
    }

        /**
     * A first name that breaks the rules is rejected
     *
     * @param value first name that is tested
     */
    @ParameterizedTest
    @MethodSource("invalidFirstNames")
    void firstName_outsideTheRules_isRejected(String value) {
        UserCreateRequest request = valid();
        request.setFirstName(value);

        assertEquals(Map.of("primer_nombre", "El primer nombre debe tener entre 2 y 30 letras."), errors(request));
    }

        /**
     * A first name that meets the rules is accepted
     *
     * @param value first name that is tested
     */
    @ParameterizedTest
    @MethodSource("validFirstNames")
    void firstName_withinTheRules_isAccepted(String value) {
        UserCreateRequest request = valid();
        request.setFirstName(value);

        assertTrue(errors(request).isEmpty());
    }

        /**
     * The present optional names with digits are rejected with the message of 2 to 30 letters
     */
    @Test
    void optionalNamesPresent_followTheSameRules() {
        UserCreateRequest request = valid();
        request.setSecondName("J9");
        request.setSecondLastName("Pérez9");

        Map<String, String> result = errors(request);

        assertEquals("El segundo nombre debe tener entre 2 y 30 letras.", result.get("segundo_nombre"));
        assertEquals("El segundo apellido debe tener entre 2 y 30 letras.", result.get("segundo_apellido"));
        assertEquals(2, result.size());
    }

        /**
     * The second name and the second lastname follow the same 2 to 30 letters as the first ones: a single letter is
     * rejected and two letters are accepted
     */
    @Test
    void optionalNames_withASingleLetter_areRejected() {
        UserCreateRequest request = valid();
        request.setSecondName("J");
        request.setSecondLastName("Z");

        Map<String, String> result = errors(request);

        assertEquals("El segundo nombre debe tener entre 2 y 30 letras.", result.get("segundo_nombre"));
        assertEquals("El segundo apellido debe tener entre 2 y 30 letras.", result.get("segundo_apellido"));
        assertEquals(2, result.size());

        request.setSecondName("Jo");
        request.setSecondLastName("Za");
        assertTrue(errors(request).isEmpty());
    }

        /**
     * The second name and the second lastname of more than 30 letters are rejected
     */
    @Test
    void optionalNames_withMoreThanThirtyLetters_areRejected() {
        UserCreateRequest request = valid();
        request.setSecondName("a".repeat(31));
        request.setSecondLastName("b".repeat(31));

        Map<String, String> result = errors(request);

        assertEquals("El segundo nombre debe tener entre 2 y 30 letras.", result.get("segundo_nombre"));
        assertEquals("El segundo apellido debe tener entre 2 y 30 letras.", result.get("segundo_apellido"));
        assertEquals(2, result.size());
    }

        /**
     * An email with an invalid format uses the generic message
     *
     * @param value email that is tested
     */
    @ParameterizedTest
    @ValueSource(strings = { "no-es-un-correo", "sin-arroba.escuelaing.edu.co", "@escuelaing.edu.co" })
    void invalidEmailFormat_usesTheGenericMessage(String value) {
        UserCreateRequest request = valid();
        request.setEmail(value);

        assertEquals(Map.of("correo", "Correo no válido."), errors(request));
    }

        /**
     * An email of more than 100 characters is rejected
     */
    @Test
    void emailLongerThan100Characters_isRejected() {
        UserCreateRequest request = valid();
        request.setEmail("a".repeat(64) + "@" + "b".repeat(29) + ".edu.co");

        assertEquals(Map.of("correo", "Correo no válido."), errors(request));
    }

        /**
     * A password outside the policy is rejected with the message of the policy
     *
     * @param value password that is tested
     */
    @ParameterizedTest
    @ValueSource(strings = { "Corta1#", "sinmayuscula1#", "SINMINUSCULA1#", "SinNumero#abc", "SinEspecial123",
            "Con espacio1#a" })
    void passwordOutsideThePolicy_isRejectedWithThePolicyMessage(String value) {
        UserCreateRequest request = valid();
        request.setPassword(value);
        request.setPasswordConfirmation(value);

        assertEquals(Map.of("contrasena", PASSWORD_MESSAGE), errors(request));
    }

        /**
     * A password of more than 64 characters is rejected
     */
    @Test
    void passwordLongerThan64Characters_isRejected() {
        String longPassword = "Aa1#" + "x".repeat(61);
        UserCreateRequest request = valid();
        request.setPassword(longPassword);
        request.setPasswordConfirmation(longPassword);

        assertEquals(Map.of("contrasena", PASSWORD_MESSAGE), errors(request));
    }

        /**
     * A password of 64 characters is accepted
     */
    @Test
    void passwordOf64Characters_isAccepted() {
        String password = "Aa1#" + "x".repeat(60);
        UserCreateRequest request = valid();
        request.setPassword(password);
        request.setPasswordConfirmation(password);

        assertTrue(errors(request).isEmpty());
    }

        /**
     * A different confirmation is reported on the confirmation field
     */
    @Test
    void differentConfirmation_isReportedOnTheConfirmationField() {
        UserCreateRequest request = valid();
        request.setPasswordConfirmation("Otra#Clave2026");

        assertEquals(Map.of("confirmar_contrasena", "Las contraseñas no coinciden."), errors(request));
    }

        /**
     * A field that is not in the contract is rejected (RG-04)
     */
    @Test
    void unknownField_isRejected_RG04() {
        UserCreateRequest request = valid();
        request.registerUnknownField("roles", "ADMIN");

        assertEquals(Map.of("roles", "El campo no está permitido."), errors(request));
    }

        /**
     * The errors are sorted by field name
     */
    @Test
    void errorsAreSortedByFieldName() {
        UserCreateRequest request = valid();
        request.setFirstName("A");
        request.setEmail("x");
        request.setPasswordConfirmation("distinta");

        assertEquals(java.util.List.of("confirmar_contrasena", "correo", "primer_nombre"),
                validator.collectErrors(request).stream().map(ErrorResponse.ErrorDetail::campo).toList());
    }
}
