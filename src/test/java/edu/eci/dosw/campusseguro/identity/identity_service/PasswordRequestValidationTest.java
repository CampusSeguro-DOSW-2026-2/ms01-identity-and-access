package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangePasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ForgotPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ResetPasswordRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.validation.RequestValidator;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/**
 * Reglas de los formularios F-04, F-05 y F-06 de la recuperación y el cambio de contraseña (RF-MS01-08) y de
 * las reglas generales RG-01, RG-02 y RG-04
 */
class PasswordRequestValidationTest {

    private static final String POLICY_MESSAGE =
            "La contraseña debe tener 8 a 64 caracteres, con mayúscula, minúscula, número y carácter especial.";
    private static final String CHANGE_NEW_MESSAGE =
            "La nueva contraseña no cumple la política o es igual a la actual.";
    private static final String MISMATCH_MESSAGE = "Las contraseñas no coinciden.";
    private static final String EMAIL_MESSAGE = "Ingrese un correo válido.";
    private static final String CODE_MESSAGE = "Código incorrecto o vencido.";
    private static final String CURRENT_MESSAGE = "La contraseña actual es incorrecta.";
    private static final String TOO_LONG_PASSWORD = "Larga#1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static ValidatorFactory factory;
    private static RequestValidator validator;

    private final ObjectMapper mapper = new ObjectMapper();

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
     * Obtiene el mensaje de cada campo inválido con el nombre que tiene en el contrato
     *
     * @param request petición que se valida
     * @return mapa de nombre de campo a mensaje
     */
    private static Map<String, String> errors(Object request) {
        Map<String, String> result = new TreeMap<>();
        for (ErrorResponse.ErrorDetail detail : validator.collectErrors(request)) {
            result.put(detail.campo(), detail.mensaje());
        }
        return result;
    }

    /**
     * Construye una solicitud de código
     *
     * @param email correo de la solicitud
     * @return la solicitud construida
     */
    private static ForgotPasswordRequest forgot(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    /**
     * Construye una petición de restablecimiento
     *
     * @param email correo de la petición
     * @param code código de la petición
     * @param password nueva contraseña
     * @param confirmation confirmación de la nueva contraseña
     * @return la petición construida
     */
    private static ResetPasswordRequest reset(String email, String code, String password, String confirmation) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(email);
        request.setCode(code);
        request.setNewPassword(password);
        request.setNewPasswordConfirmation(confirmation);
        return request;
    }

    /**
     * Construye una petición de cambio de contraseña
     *
     * @param current contraseña actual
     * @param password nueva contraseña
     * @param confirmation confirmación de la nueva contraseña
     * @return la petición construida
     */
    private static ChangePasswordRequest change(String current, String password, String confirmation) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(password);
        request.setNewPasswordConfirmation(confirmation);
        return request;
    }

    /**
     * Una solicitud de código válida no produce errores
     */
    @Test
    void forgot_validRequest_hasNoErrors() {
        assertTrue(errors(forgot("maria.perez@escuelaing.edu.co")).isEmpty());
    }

    /**
     * El correo se recorta y se pasa a minúsculas (RG-01)
     */
    @Test
    void forgot_email_isTrimmedAndLowercased() {
        ForgotPasswordRequest request = forgot("  Maria.Perez@EscuelaIng.edu.co \t");

        assertEquals("maria.perez@escuelaing.edu.co", request.getEmail());
        assertTrue(errors(request).isEmpty());
    }

    /**
     * No se exige el dominio institucional para no revelar qué correos son válidos, solo el formato
     */
    @Test
    void forgot_externalDomain_isAccepted() {
        assertTrue(errors(forgot("alguien@gmail.com")).isEmpty());
    }

    /**
     * Un correo faltante, vacío, con solo espacios o con formato inválido tiene el mensaje del formulario F-04
     *
     * @param email correo recibido
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "sin-arroba", "a@", "@escuelaing.edu.co", "dos@@escuelaing.edu.co" })
    void forgot_missingOrMalformedEmail_hasTheFormMessage(String email) {
        assertEquals(Map.of("correo", EMAIL_MESSAGE), errors(forgot(email)));
    }

    /**
     * Un correo de más de 100 caracteres se rechaza
     */
    @Test
    void forgot_emailLongerThan100_isRejected() {
        String email = "a".repeat(90) + "@escuelaing.edu.co";

        assertEquals(Map.of("correo", EMAIL_MESSAGE), errors(forgot(email)));
    }

    /**
     * Sin el campo correo se rechaza con el mensaje del formulario
     */
    @Test
    void forgot_withoutEmail_isRejected() {
        assertEquals(Map.of("correo", EMAIL_MESSAGE), errors(new ForgotPasswordRequest()));
    }

    /**
     * Un campo fuera del contrato se rechaza e indica cuál es (RG-04)
     *
     * @throws Exception si falla la lectura del JSON
     */
    @Test
    void forgot_unknownField_isRejectedByName() throws Exception {
        ForgotPasswordRequest request = mapper.readValue(
                "{\"correo\":\"maria.perez@escuelaing.edu.co\",\"codigo\":\"123456\"}",
                ForgotPasswordRequest.class);

        assertEquals(Map.of("codigo", "El campo no está permitido."), errors(request));
    }

    /**
     * Una petición de restablecimiento válida no produce errores
     */
    @Test
    void reset_validRequest_hasNoErrors() {
        assertTrue(errors(reset("maria.perez@escuelaing.edu.co", "482913", "Nueva#2026x", "Nueva#2026x"))
                .isEmpty());
    }

    /**
     * El código acepta ceros a la izquierda y se recorta (RG-01)
     */
    @Test
    void reset_codeWithLeadingZeros_isAcceptedAndTrimmed() {
        ResetPasswordRequest request = reset("maria.perez@escuelaing.edu.co", " 007391 ", "Nueva#2026x",
                "Nueva#2026x");

        assertEquals("007391", request.getCode());
        assertTrue(errors(request).isEmpty());
    }

    /**
     * Un código que no son exactamente 6 dígitos tiene el mensaje genérico del formulario F-05
     *
     * @param code código recibido
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "12345", "1234567", "12345a", "12 345", "１２３４５６", "-12345", "12.345" })
    void reset_codeThatIsNotSixDigits_hasTheGenericMessage(String code) {
        assertEquals(Map.of("codigo", CODE_MESSAGE),
                errors(reset("maria.perez@escuelaing.edu.co", code, "Nueva#2026x", "Nueva#2026x")));
    }

    /**
     * Un correo con formato inválido tiene el mensaje del formulario F-05
     */
    @Test
    void reset_malformedEmail_hasTheFormMessage() {
        assertEquals(Map.of("correo", EMAIL_MESSAGE),
                errors(reset("no-es-un-correo", "482913", "Nueva#2026x", "Nueva#2026x")));
    }

    /**
     * Una nueva contraseña que incumple la política tiene el mensaje del formulario F-05
     *
     * @param password nueva contraseña recibida
     */
    @ParameterizedTest
    @ValueSource(strings = { "Ab1#", "sinmayuscula1#", "SINMINUSCULA1#", "SinNumero#abc", "SinEspecial123",
            "Con espacio1#", TOO_LONG_PASSWORD })
    void reset_newPasswordOutsideThePolicy_hasThePolicyMessage(String password) {
        assertEquals(Map.of("nueva_contrasena", POLICY_MESSAGE),
                errors(reset("maria.perez@escuelaing.edu.co", "482913", password, password)));
    }

    /**
     * Una confirmación distinta tiene el mensaje del formulario F-05 y se asocia al campo de confirmación
     */
    @Test
    void reset_confirmationDoesNotMatch_isReportedOnTheConfirmationField() {
        assertEquals(Map.of("confirmar_nueva_contrasena", MISMATCH_MESSAGE),
                errors(reset("maria.perez@escuelaing.edu.co", "482913", "Nueva#2026x", "Otra#2026xyz")));
    }

    /**
     * La contraseña y su confirmación no se recortan: un espacio al final hace que no coincidan (RG-01)
     */
    @Test
    void reset_passwordsAreNotTrimmed() {
        ResetPasswordRequest request = reset("maria.perez@escuelaing.edu.co", "482913", "Nueva#2026x",
                "Nueva#2026x ");

        assertEquals("Nueva#2026x ", request.getNewPasswordConfirmation());
        assertEquals(Map.of("confirmar_nueva_contrasena", MISMATCH_MESSAGE), errors(request));
    }

    /**
     * Un cuerpo vacío informa todos los campos obligatorios a la vez
     */
    @Test
    void reset_emptyRequest_reportsEveryRequiredField() {
        Map<String, String> result = errors(new ResetPasswordRequest());

        assertEquals(EMAIL_MESSAGE, result.get("correo"));
        assertEquals(CODE_MESSAGE, result.get("codigo"));
        assertEquals(POLICY_MESSAGE, result.get("nueva_contrasena"));
        assertEquals(MISMATCH_MESSAGE, result.get("confirmar_nueva_contrasena"));
        assertEquals(4, result.size());
    }

    /**
     * El nombre de la confirmación del registro no es válido en este formulario: se rechaza como campo
     * desconocido y la confirmación queda faltante
     *
     * @throws Exception si falla la lectura del JSON
     */
    @Test
    void reset_registrationConfirmationName_isRejected() throws Exception {
        ResetPasswordRequest request = mapper.readValue("{\"correo\":\"maria.perez@escuelaing.edu.co\","
                + "\"codigo\":\"482913\",\"nueva_contrasena\":\"Nueva#2026x\","
                + "\"confirmar_contrasena\":\"Nueva#2026x\"}", ResetPasswordRequest.class);

        Map<String, String> result = errors(request);

        assertEquals("El campo no está permitido.", result.get("confirmar_contrasena"));
        assertEquals(MISMATCH_MESSAGE, result.get("confirmar_nueva_contrasena"));
    }

    /**
     * Los nombres del contrato se leen del JSON
     *
     * @throws Exception si falla la lectura del JSON
     */
    @Test
    void reset_json_isMappedWithTheContractNames() throws Exception {
        ResetPasswordRequest request = mapper.readValue("{\"correo\":\"Maria.Perez@escuelaing.edu.co\","
                + "\"codigo\":\"482913\",\"nueva_contrasena\":\"Nueva#2026x\","
                + "\"confirmar_nueva_contrasena\":\"Nueva#2026x\"}", ResetPasswordRequest.class);

        assertEquals("maria.perez@escuelaing.edu.co", request.getEmail());
        assertEquals("482913", request.getCode());
        assertEquals("Nueva#2026x", request.getNewPassword());
        assertEquals("Nueva#2026x", request.getNewPasswordConfirmation());
        assertTrue(errors(request).isEmpty());
    }

    /**
     * Una petición de cambio válida no produce errores
     */
    @Test
    void change_validRequest_hasNoErrors() {
        assertTrue(errors(change("Campus#2026", "Nueva#2026x", "Nueva#2026x")).isEmpty());
    }

    /**
     * La contraseña actual no se valida contra la política: solo debe existir y no superar los 64 caracteres, la
     * decide el servicio contra la almacenada
     */
    @Test
    void change_currentPassword_onlyRequiresPresenceAndMaxLength() {
        assertTrue(errors(change("x", "Nueva#2026x", "Nueva#2026x")).isEmpty());
        assertTrue(errors(change("a".repeat(64), "Nueva#2026x", "Nueva#2026x")).isEmpty());
        assertEquals(Map.of("contrasena_actual", CURRENT_MESSAGE),
                errors(change("a".repeat(65), "Nueva#2026x", "Nueva#2026x")));
    }

    /**
     * Una contraseña actual vacía o con solo espacios tiene el mensaje del formulario F-06
     *
     * @param current contraseña actual recibida
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void change_blankCurrentPassword_hasTheFormMessage(String current) {
        assertEquals(Map.of("contrasena_actual", CURRENT_MESSAGE),
                errors(change(current, "Nueva#2026x", "Nueva#2026x")));
    }

    /**
     * Una nueva contraseña que incumple la política tiene el mensaje del formulario F-06, que también cubre el
     * caso de ser igual a la actual
     *
     * @param password nueva contraseña recibida
     */
    @ParameterizedTest
    @ValueSource(strings = { "Ab1#", "sinmayuscula1#", "SINMINUSCULA1#", "SinNumero#abc", "SinEspecial123",
            "Con espacio1#", TOO_LONG_PASSWORD })
    void change_newPasswordOutsideThePolicy_hasTheFormMessage(String password) {
        assertEquals(Map.of("nueva_contrasena", CHANGE_NEW_MESSAGE),
                errors(change("Campus#2026", password, password)));
    }

    /**
     * Una confirmación distinta tiene el mensaje del formulario F-06 y se asocia al campo de confirmación
     */
    @Test
    void change_confirmationDoesNotMatch_isReportedOnTheConfirmationField() {
        assertEquals(Map.of("confirmar_nueva_contrasena", MISMATCH_MESSAGE),
                errors(change("Campus#2026", "Nueva#2026x", "Otra#2026xyz")));
    }

    /**
     * Un cuerpo vacío informa todos los campos obligatorios a la vez
     */
    @Test
    void change_emptyRequest_reportsEveryRequiredField() {
        Map<String, String> result = errors(new ChangePasswordRequest());

        assertEquals(CURRENT_MESSAGE, result.get("contrasena_actual"));
        assertEquals(CHANGE_NEW_MESSAGE, result.get("nueva_contrasena"));
        assertEquals(MISMATCH_MESSAGE, result.get("confirmar_nueva_contrasena"));
        assertEquals(3, result.size());
    }

    /**
     * Las contraseñas no se recortan ni se normalizan (RG-01)
     */
    @Test
    void change_passwords_areKeptExactlyAsReceived() {
        ChangePasswordRequest request = change(" Campus#2026 ", "Nueva#2026x", "Nueva#2026x");

        assertEquals(" Campus#2026 ", request.getCurrentPassword());
    }

    /**
     * Un campo fuera del contrato, como el correo, el id o el estado, se rechaza e indica cuál es (RG-04)
     *
     * @throws Exception si falla la lectura del JSON
     */
    @Test
    void change_unknownFields_areRejectedByName() throws Exception {
        ChangePasswordRequest request = mapper.readValue("{\"contrasena_actual\":\"Campus#2026\","
                + "\"nueva_contrasena\":\"Nueva#2026x\",\"confirmar_nueva_contrasena\":\"Nueva#2026x\","
                + "\"id\":\"3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42\",\"correo\":\"otro@escuelaing.edu.co\"}",
                ChangePasswordRequest.class);

        Map<String, String> result = errors(request);

        assertEquals("El campo no está permitido.", result.get("id"));
        assertEquals("El campo no está permitido.", result.get("correo"));
        assertEquals(2, result.size());
    }

    /**
     * Los nombres del contrato se leen del JSON
     *
     * @throws Exception si falla la lectura del JSON
     */
    @Test
    void change_json_isMappedWithTheContractNames() throws Exception {
        ChangePasswordRequest request = mapper.readValue("{\"contrasena_actual\":\"Campus#2026\","
                + "\"nueva_contrasena\":\"Nueva#2026x\",\"confirmar_nueva_contrasena\":\"Nueva#2026x\"}",
                ChangePasswordRequest.class);

        assertEquals("Campus#2026", request.getCurrentPassword());
        assertEquals("Nueva#2026x", request.getNewPassword());
        assertEquals("Nueva#2026x", request.getNewPasswordConfirmation());
        assertNull(errors(request).get("contrasena_actual"));
        assertTrue(errors(request).isEmpty());
    }
}
