package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Rules of form F-02 (email: format and maximum 100; password: not empty, maximum 64) and of the general rules RG-01
 * and RG-02
 */
class LoginRequestValidationTest {

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
     * Builds a login request
     *
     * @param correo email of the request
     * @param contrasena password of the request
     * @return the built request
     */
    private static LoginRequest request(String correo, String contrasena) {
        LoginRequest request = new LoginRequest();
        request.setCorreo(correo);
        request.setContrasena(contrasena);
        return request;
    }

        /**
     * Gets the message of each invalid field
     *
     * @param request request that is validated
     * @return map from field name to message
     */
    private static Map<String, String> violations(LoginRequest request) {
        Map<String, String> result = new TreeMap<>();
        for (ConstraintViolation<LoginRequest> v : validator.validate(request)) {
            result.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return result;
    }

        /**
     * A valid request produces no violations
     */
    @Test
    void validRequest_hasNoViolations() {
        assertTrue(violations(request("maria.perez@escuelaing.edu.co", "Campus#2026")).isEmpty());
    }

        /**
     * The email is trimmed and converted to lowercase (RG-01)
     */
    @Test
    void email_isTrimmedAndLowercased_RG01() {
        LoginRequest request = request("  Maria.Perez@EscuelaIng.edu.co \t", "Campus#2026");

        assertEquals("maria.perez@escuelaing.edu.co", request.getCorreo());
        assertTrue(violations(request).isEmpty());
    }

        /**
     * The password is never trimmed or modified (RG-01)
     */
    @Test
    void password_isNeverTrimmedOrChanged_RG01() {
        assertEquals(" Campus#2026 ", request("maria.perez@escuelaing.edu.co", " Campus#2026 ").getContrasena());
    }

        /**
     * An email with an invalid format is rejected with the message of F-02
     */
    @Test
    void invalidEmailFormat_isRejectedWithTheF02Message() {
        assertEquals(Map.of("correo", "Ingrese un correo válido."),
                violations(request("esto-no-es-un-correo", "Campus#2026")));
    }

        /**
     * An absent or blank email is treated as missing (RG-02)
     */
    @Test
    void missingOrBlankEmail_isTreatedAsMissing_RG02() {
        assertEquals(Map.of("correo", "Ingrese un correo válido."), violations(request(null, "Campus#2026")));
        assertEquals(Map.of("correo", "Ingrese un correo válido."), violations(request("", "Campus#2026")));
        assertEquals(Map.of("correo", "Ingrese un correo válido."), violations(request("   ", "Campus#2026")));
    }

        /**
     * An email of exactly 100 characters is accepted
     */
    @Test
    void emailOfExactly100Characters_isAccepted() {
        String email = "a".repeat(64) + "@" + "b".repeat(28) + ".edu.co"; // 64 + 1 + 35 = 100
        assertEquals(100, email.length());

        assertTrue(violations(request(email, "Campus#2026")).isEmpty());
    }

        /**
     * An email of more than 100 characters is rejected
     */
    @Test
    void emailLongerThan100Characters_isRejected() {
        String email = "a".repeat(64) + "@" + "b".repeat(29) + ".edu.co"; // 101

        assertEquals(Map.of("correo", "Ingrese un correo válido."), violations(request(email, "Campus#2026")));
    }

        /**
     * An absent or blank password is rejected with the message of F-02
     */
    @Test
    void missingOrBlankPassword_isRejectedWithTheF02Message() {
        String email = "maria.perez@escuelaing.edu.co";

        assertEquals(Map.of("contrasena", "Ingrese su contraseña."), violations(request(email, null)));
        assertEquals(Map.of("contrasena", "Ingrese su contraseña."), violations(request(email, "")));
        assertEquals(Map.of("contrasena", "Ingrese su contraseña."), violations(request(email, "    ")));
    }

        /**
     * The password policy is not validated again in the login
     */
    @Test
    void passwordPolicyIsNotRevalidatedAtLogin() {
        // F-02: "No se revalida la politica en el login". A short password or one without symbols is still a valid
        // input; it will be the hash that decides whether it matches.
        assertTrue(violations(request("maria.perez@escuelaing.edu.co", "a")).isEmpty());
    }

        /**
     * A password of 64 characters is accepted and one of 65 is rejected
     */
    @Test
    void passwordOf64Characters_isAcceptedAndOf65IsRejected() {
        String email = "maria.perez@escuelaing.edu.co";

        assertTrue(violations(request(email, "a".repeat(64))).isEmpty());
        assertEquals(Map.of("contrasena", "La contraseña no puede superar los 64 caracteres."),
                violations(request(email, "a".repeat(65))));
    }

        /**
     * If both fields are missing both violations are reported
     */
    @Test
    void bothFieldsMissing_reportsBothViolations() {
        Map<String, String> result = violations(new LoginRequest());

        assertEquals(2, result.size());
        assertEquals("Ingrese un correo válido.", result.get("correo"));
        assertEquals("Ingrese su contraseña.", result.get("contrasena"));
    }

        /**
     * A null email remains null after the normalization
     */
    @Test
    void nullEmailStaysNullAfterNormalization() {
        LoginRequest request = new LoginRequest();
        request.setCorreo(null);

        assertNull(request.getCorreo());
    }
}
