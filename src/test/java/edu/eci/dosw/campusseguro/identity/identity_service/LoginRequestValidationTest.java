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
 * Reglas del formulario F-02 (correo: formato y maximo 100; contrasena: no vacia, maximo 64) y de
 * las reglas generales RG-01 y RG-02
 */
class LoginRequestValidationTest {

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
     * Construye una petición de login
     *
     * @param correo correo de la petición
     * @param contrasena contraseña de la petición
     * @return la petición construida
     */
    private static LoginRequest request(String correo, String contrasena) {
        LoginRequest request = new LoginRequest();
        request.setCorreo(correo);
        request.setContrasena(contrasena);
        return request;
    }

    /**
     * Obtiene el mensaje de cada campo inválido
     *
     * @param request petición que se valida
     * @return mapa de nombre de campo a mensaje
     */
    private static Map<String, String> violations(LoginRequest request) {
        Map<String, String> result = new TreeMap<>();
        for (ConstraintViolation<LoginRequest> v : validator.validate(request)) {
            result.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return result;
    }

    /**
     * Una petición válida no produce violaciones
     */
    @Test
    void validRequest_hasNoViolations() {
        assertTrue(violations(request("maria.perez@escuelaing.edu.co", "Campus#2026")).isEmpty());
    }

    /**
     * El correo se recorta y se pasa a minúsculas (RG-01)
     */
    @Test
    void email_isTrimmedAndLowercased_RG01() {
        LoginRequest request = request("  Maria.Perez@EscuelaIng.edu.co \t", "Campus#2026");

        assertEquals("maria.perez@escuelaing.edu.co", request.getCorreo());
        assertTrue(violations(request).isEmpty());
    }

    /**
     * La contraseña nunca se recorta ni se modifica (RG-01)
     */
    @Test
    void password_isNeverTrimmedOrChanged_RG01() {
        assertEquals(" Campus#2026 ", request("maria.perez@escuelaing.edu.co", " Campus#2026 ").getContrasena());
    }

    /**
     * Un correo con formato inválido se rechaza con el mensaje de F-02
     */
    @Test
    void invalidEmailFormat_isRejectedWithTheF02Message() {
        assertEquals(Map.of("correo", "Ingrese un correo válido."),
                violations(request("esto-no-es-un-correo", "Campus#2026")));
    }

    /**
     * Un correo ausente o en blanco se trata como faltante (RG-02)
     */
    @Test
    void missingOrBlankEmail_isTreatedAsMissing_RG02() {
        assertEquals(Map.of("correo", "Ingrese un correo válido."), violations(request(null, "Campus#2026")));
        assertEquals(Map.of("correo", "Ingrese un correo válido."), violations(request("", "Campus#2026")));
        assertEquals(Map.of("correo", "Ingrese un correo válido."), violations(request("   ", "Campus#2026")));
    }

    /**
     * Un correo de exactamente 100 caracteres se acepta
     */
    @Test
    void emailOfExactly100Characters_isAccepted() {
        String email = "a".repeat(64) + "@" + "b".repeat(28) + ".edu.co"; // 64 + 1 + 35 = 100
        assertEquals(100, email.length());

        assertTrue(violations(request(email, "Campus#2026")).isEmpty());
    }

    /**
     * Un correo de más de 100 caracteres se rechaza
     */
    @Test
    void emailLongerThan100Characters_isRejected() {
        String email = "a".repeat(64) + "@" + "b".repeat(29) + ".edu.co"; // 101

        assertEquals(Map.of("correo", "Ingrese un correo válido."), violations(request(email, "Campus#2026")));
    }

    /**
     * Una contraseña ausente o en blanco se rechaza con el mensaje de F-02
     */
    @Test
    void missingOrBlankPassword_isRejectedWithTheF02Message() {
        String email = "maria.perez@escuelaing.edu.co";

        assertEquals(Map.of("contrasena", "Ingrese su contraseña."), violations(request(email, null)));
        assertEquals(Map.of("contrasena", "Ingrese su contraseña."), violations(request(email, "")));
        assertEquals(Map.of("contrasena", "Ingrese su contraseña."), violations(request(email, "    ")));
    }

    /**
     * La política de contraseña no se vuelve a validar en el login
     */
    @Test
    void passwordPolicyIsNotRevalidatedAtLogin() {
        // F-02: "No se revalida la politica en el login". Una contrasena corta o sin simbolos sigue siendo
        // una entrada valida; sera el hash quien decida si coincide.
        assertTrue(violations(request("maria.perez@escuelaing.edu.co", "a")).isEmpty());
    }

    /**
     * Una contraseña de 64 caracteres se acepta y una de 65 se rechaza
     */
    @Test
    void passwordOf64Characters_isAcceptedAndOf65IsRejected() {
        String email = "maria.perez@escuelaing.edu.co";

        assertTrue(violations(request(email, "a".repeat(64))).isEmpty());
        assertEquals(Map.of("contrasena", "La contraseña no puede superar los 64 caracteres."),
                violations(request(email, "a".repeat(65))));
    }

    /**
     * Si faltan los dos campos se informan ambas violaciones
     */
    @Test
    void bothFieldsMissing_reportsBothViolations() {
        Map<String, String> result = violations(new LoginRequest());

        assertEquals(2, result.size());
        assertEquals("Ingrese un correo válido.", result.get("correo"));
        assertEquals("Ingrese su contraseña.", result.get("contrasena"));
    }

    /**
     * Un correo nulo sigue siendo nulo tras la normalización
     */
    @Test
    void nullEmailStaysNullAfterNormalization() {
        LoginRequest request = new LoginRequest();
        request.setCorreo(null);

        assertNull(request.getCorreo());
    }
}
