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
 * Reglas del formulario F-01 de registro y de las reglas generales RG-01, RG-02 y RG-04
 */
class UserCreateRequestValidationTest {

    private static final String PASSWORD = "MiPassword#2026";
    private static final String PASSWORD_MESSAGE =
            "La contraseña debe tener 8 a 64 caracteres, con mayúscula, minúscula, número y carácter especial.";

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
     * Proporciona primeros nombres que incumplen las reglas
     *
     * @return los valores inválidos
     */
    private static Stream<String> invalidFirstNames() {
        return Stream.of("A", "Ana123", "Ana#", "a".repeat(31));
    }

    /**
     * Proporciona primeros nombres que cumplen las reglas
     *
     * @return los valores válidos
     */
    private static Stream<String> validFirstNames() {
        return Stream.of("Ana", "Jean-Luc", "D'Artagnan", "De la Cruz", "Ñandú", "ab", "a".repeat(30));
    }

    /**
     * Construye una petición de registro válida
     *
     * @return la petición válida
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
     * Obtiene el mensaje de error de cada campo inválido
     *
     * @param request petición que se valida
     * @return mapa de nombre de campo a mensaje, ordenado por campo
     */
    private static Map<String, String> errors(UserCreateRequest request) {
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
        assertTrue(errors(valid()).isEmpty());
    }

    /**
     * Los nombres opcionales pueden omitirse
     */
    @Test
    void optionalNamesMayBeOmitted() {
        UserCreateRequest request = valid();
        request.setSecondName(null);
        request.setSecondLastName(null);

        assertTrue(errors(request).isEmpty());
    }

    /**
     * Los nombres opcionales en blanco se tratan como ausentes (RG-02)
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
     * Los campos de texto se recortan (RG-01)
     */
    @Test
    void textFields_areTrimmed_RG01() {
        UserCreateRequest request = valid();
        request.setFirstName("  Ana  ");

        assertEquals("Ana", request.getFirstName());
    }

    /**
     * El correo se recorta y se pasa a minúsculas (RG-01)
     */
    @Test
    void email_isTrimmedAndLowercased_RG01() {
        UserCreateRequest request = valid();
        request.setEmail("  Maria.Perez@EscuelaIng.edu.co ");

        assertEquals("maria.perez@escuelaing.edu.co", request.getEmail());
        assertTrue(errors(request).isEmpty());
    }

    /**
     * La contraseña nunca se recorta (RG-01)
     */
    @Test
    void password_isNeverTrimmed_RG01() {
        UserCreateRequest request = valid();
        request.setPassword(" " + PASSWORD + " ");

        assertEquals(" " + PASSWORD + " ", request.getPassword());
    }

    /**
     * Los campos obligatorios ausentes producen un mensaje por campo
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
     * Un primer nombre que incumple las reglas se rechaza
     *
     * @param value primer nombre que se prueba
     */
    @ParameterizedTest
    @MethodSource("invalidFirstNames")
    void firstName_outsideTheRules_isRejected(String value) {
        UserCreateRequest request = valid();
        request.setFirstName(value);

        assertEquals(Map.of("primer_nombre", "El primer nombre debe tener entre 2 y 30 letras."), errors(request));
    }

    /**
     * Un primer nombre que cumple las reglas se acepta
     *
     * @param value primer nombre que se prueba
     */
    @ParameterizedTest
    @MethodSource("validFirstNames")
    void firstName_withinTheRules_isAccepted(String value) {
        UserCreateRequest request = valid();
        request.setFirstName(value);

        assertTrue(errors(request).isEmpty());
    }

    /**
     * Los nombres opcionales presentes siguen las mismas reglas
     */
    @Test
    void optionalNamesPresent_followTheSameRules() {
        UserCreateRequest request = valid();
        request.setSecondName("J");
        request.setSecondLastName("Pérez9");

        Map<String, String> result = errors(request);

        assertEquals("El segundo nombre debe tener entre 2 y 30 letras.", result.get("segundo_nombre"));
        assertEquals("El segundo apellido debe tener entre 2 y 30 letras.", result.get("segundo_apellido"));
        assertEquals(2, result.size());
    }

    /**
     * Un correo con formato inválido usa el mensaje genérico
     *
     * @param value correo que se prueba
     */
    @ParameterizedTest
    @ValueSource(strings = { "no-es-un-correo", "sin-arroba.escuelaing.edu.co", "@escuelaing.edu.co" })
    void invalidEmailFormat_usesTheGenericMessage(String value) {
        UserCreateRequest request = valid();
        request.setEmail(value);

        assertEquals(Map.of("correo", "Correo no válido."), errors(request));
    }

    /**
     * Un correo de más de 100 caracteres se rechaza
     */
    @Test
    void emailLongerThan100Characters_isRejected() {
        UserCreateRequest request = valid();
        request.setEmail("a".repeat(64) + "@" + "b".repeat(29) + ".edu.co");

        assertEquals(Map.of("correo", "Correo no válido."), errors(request));
    }

    /**
     * Una contraseña fuera de la política se rechaza con el mensaje de la política
     *
     * @param value contraseña que se prueba
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
     * Una contraseña de más de 64 caracteres se rechaza
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
     * Una contraseña de 64 caracteres se acepta
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
     * Una confirmación distinta se informa sobre el campo de confirmación
     */
    @Test
    void differentConfirmation_isReportedOnTheConfirmationField() {
        UserCreateRequest request = valid();
        request.setPasswordConfirmation("Otra#Clave2026");

        assertEquals(Map.of("confirmar_contrasena", "Las contraseñas no coinciden."), errors(request));
    }

    /**
     * Un campo que no está en el contrato se rechaza (RG-04)
     */
    @Test
    void unknownField_isRejected_RG04() {
        UserCreateRequest request = valid();
        request.registerUnknownField("roles", "ADMIN");

        assertEquals(Map.of("roles", "El campo no está permitido."), errors(request));
    }

    /**
     * Los errores se ordenan por nombre de campo
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
