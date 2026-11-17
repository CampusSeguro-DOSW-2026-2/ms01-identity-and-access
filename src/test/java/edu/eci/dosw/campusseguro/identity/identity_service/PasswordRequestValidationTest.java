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
 * Rules of forms F-04, F-05 and F-06 of the password recovery and change (RF-MS01-08) and of the general rules RG-01,
 * RG-02 and RG-04
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
     * Gets the message of each invalid field with the name it has in the contract
     *
     * @param request request that is validated
     * @return map from field name to message
     */
    private static Map<String, String> errors(Object request) {
        Map<String, String> result = new TreeMap<>();
        for (ErrorResponse.ErrorDetail detail : validator.collectErrors(request)) {
            result.put(detail.campo(), detail.mensaje());
        }
        return result;
    }

        /**
     * Builds a code request
     *
     * @param email email of the request
     * @return the built request
     */
    private static ForgotPasswordRequest forgot(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

        /**
     * Builds a reset request
     *
     * @param email email of the request
     * @param code code of the request
     * @param password new password
     * @param confirmation confirmation of the new password
     * @return the built request
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
     * Builds a password change request
     *
     * @param current current password
     * @param password new password
     * @param confirmation confirmation of the new password
     * @return the built request
     */
    private static ChangePasswordRequest change(String current, String password, String confirmation) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(password);
        request.setNewPasswordConfirmation(confirmation);
        return request;
    }

        /**
     * A valid code request produces no errors
     */
    @Test
    void forgot_validRequest_hasNoErrors() {
        assertTrue(errors(forgot("maria.perez@escuelaing.edu.co")).isEmpty());
    }

        /**
     * The email is trimmed and converted to lowercase (RG-01)
     */
    @Test
    void forgot_email_isTrimmedAndLowercased() {
        ForgotPasswordRequest request = forgot("  Maria.Perez@EscuelaIng.edu.co \t");

        assertEquals("maria.perez@escuelaing.edu.co", request.getEmail());
        assertTrue(errors(request).isEmpty());
    }

        /**
     * The institutional domain is not required so as not to reveal which emails are valid, only the format
     */
    @Test
    void forgot_externalDomain_isAccepted() {
        assertTrue(errors(forgot("alguien@gmail.com")).isEmpty());
    }

        /**
     * A missing, empty, spaces-only or invalid-format email has the message of form F-04
     *
     * @param email email received
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "sin-arroba", "a@", "@escuelaing.edu.co", "dos@@escuelaing.edu.co" })
    void forgot_missingOrMalformedEmail_hasTheFormMessage(String email) {
        assertEquals(Map.of("correo", EMAIL_MESSAGE), errors(forgot(email)));
    }

        /**
     * An email of more than 100 characters is rejected
     */
    @Test
    void forgot_emailLongerThan100_isRejected() {
        String email = "a".repeat(90) + "@escuelaing.edu.co";

        assertEquals(Map.of("correo", EMAIL_MESSAGE), errors(forgot(email)));
    }

        /**
     * Without the correo field it is rejected with the message of the form
     */
    @Test
    void forgot_withoutEmail_isRejected() {
        assertEquals(Map.of("correo", EMAIL_MESSAGE), errors(new ForgotPasswordRequest()));
    }

        /**
     * A field outside the contract is rejected and indicates which one it is (RG-04)
     *
     * @throws Exception if reading the JSON fails
     */
    @Test
    void forgot_unknownField_isRejectedByName() throws Exception {
        ForgotPasswordRequest request = mapper.readValue(
                "{\"correo\":\"maria.perez@escuelaing.edu.co\",\"codigo\":\"123456\"}",
                ForgotPasswordRequest.class);

        assertEquals(Map.of("codigo", "El campo no está permitido."), errors(request));
    }

        /**
     * A valid reset request produces no errors
     */
    @Test
    void reset_validRequest_hasNoErrors() {
        assertTrue(errors(reset("maria.perez@escuelaing.edu.co", "482913", "Nueva#2026x", "Nueva#2026x"))
                .isEmpty());
    }

        /**
     * The code accepts leading zeros and is trimmed (RG-01)
     */
    @Test
    void reset_codeWithLeadingZeros_isAcceptedAndTrimmed() {
        ResetPasswordRequest request = reset("maria.perez@escuelaing.edu.co", " 007391 ", "Nueva#2026x",
                "Nueva#2026x");

        assertEquals("007391", request.getCode());
        assertTrue(errors(request).isEmpty());
    }

        /**
     * A code that is not exactly 6 digits has the generic message of form F-05
     *
     * @param code code received
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "12345", "1234567", "12345a", "12 345", "１２３４５６", "-12345", "12.345" })
    void reset_codeThatIsNotSixDigits_hasTheGenericMessage(String code) {
        assertEquals(Map.of("codigo", CODE_MESSAGE),
                errors(reset("maria.perez@escuelaing.edu.co", code, "Nueva#2026x", "Nueva#2026x")));
    }

        /**
     * An email with an invalid format has the message of form F-05
     */
    @Test
    void reset_malformedEmail_hasTheFormMessage() {
        assertEquals(Map.of("correo", EMAIL_MESSAGE),
                errors(reset("no-es-un-correo", "482913", "Nueva#2026x", "Nueva#2026x")));
    }

        /**
     * A new password that breaks the policy has the message of form F-05
     *
     * @param password new password received
     */
    @ParameterizedTest
    @ValueSource(strings = { "Ab1#", "sinmayuscula1#", "SINMINUSCULA1#", "SinNumero#abc", "SinEspecial123",
            "Con espacio1#", TOO_LONG_PASSWORD })
    void reset_newPasswordOutsideThePolicy_hasThePolicyMessage(String password) {
        assertEquals(Map.of("nueva_contrasena", POLICY_MESSAGE),
                errors(reset("maria.perez@escuelaing.edu.co", "482913", password, password)));
    }

        /**
     * A different confirmation has the message of form F-05 and is associated with the confirmation field
     */
    @Test
    void reset_confirmationDoesNotMatch_isReportedOnTheConfirmationField() {
        assertEquals(Map.of("confirmar_nueva_contrasena", MISMATCH_MESSAGE),
                errors(reset("maria.perez@escuelaing.edu.co", "482913", "Nueva#2026x", "Otra#2026xyz")));
    }

        /**
     * The password and its confirmation are not trimmed: a trailing space makes them not match (RG-01)
     */
    @Test
    void reset_passwordsAreNotTrimmed() {
        ResetPasswordRequest request = reset("maria.perez@escuelaing.edu.co", "482913", "Nueva#2026x",
                "Nueva#2026x ");

        assertEquals("Nueva#2026x ", request.getNewPasswordConfirmation());
        assertEquals(Map.of("confirmar_nueva_contrasena", MISMATCH_MESSAGE), errors(request));
    }

        /**
     * An empty body reports all the required fields at once
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
     * The confirmation name of the registration is not valid in this form: it is rejected as an unknown field and the
     * confirmation is left missing
     *
     * @throws Exception if reading the JSON fails
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
     * The names of the contract are read from the JSON
     *
     * @throws Exception if reading the JSON fails
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
     * A valid change request produces no errors
     */
    @Test
    void change_validRequest_hasNoErrors() {
        assertTrue(errors(change("Campus#2026", "Nueva#2026x", "Nueva#2026x")).isEmpty());
    }

        /**
     * The current password is not validated against the policy: it only has to exist and not exceed 64 characters, the
     * service decides it against the stored one
     */
    @Test
    void change_currentPassword_onlyRequiresPresenceAndMaxLength() {
        assertTrue(errors(change("x", "Nueva#2026x", "Nueva#2026x")).isEmpty());
        assertTrue(errors(change("a".repeat(64), "Nueva#2026x", "Nueva#2026x")).isEmpty());
        assertEquals(Map.of("contrasena_actual", CURRENT_MESSAGE),
                errors(change("a".repeat(65), "Nueva#2026x", "Nueva#2026x")));
    }

        /**
     * An empty or spaces-only current password has the message of form F-06
     *
     * @param current current password received
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void change_blankCurrentPassword_hasTheFormMessage(String current) {
        assertEquals(Map.of("contrasena_actual", CURRENT_MESSAGE),
                errors(change(current, "Nueva#2026x", "Nueva#2026x")));
    }

        /**
     * A new password that breaks the policy has the message of form F-06, which also covers the case of being equal to
     * the current one
     *
     * @param password new password received
     */
    @ParameterizedTest
    @ValueSource(strings = { "Ab1#", "sinmayuscula1#", "SINMINUSCULA1#", "SinNumero#abc", "SinEspecial123",
            "Con espacio1#", TOO_LONG_PASSWORD })
    void change_newPasswordOutsideThePolicy_hasTheFormMessage(String password) {
        assertEquals(Map.of("nueva_contrasena", CHANGE_NEW_MESSAGE),
                errors(change("Campus#2026", password, password)));
    }

        /**
     * A different confirmation has the message of form F-06 and is associated with the confirmation field
     */
    @Test
    void change_confirmationDoesNotMatch_isReportedOnTheConfirmationField() {
        assertEquals(Map.of("confirmar_nueva_contrasena", MISMATCH_MESSAGE),
                errors(change("Campus#2026", "Nueva#2026x", "Otra#2026xyz")));
    }

        /**
     * An empty body reports all the required fields at once
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
     * The passwords are not trimmed or normalized (RG-01)
     */
    @Test
    void change_passwords_areKeptExactlyAsReceived() {
        ChangePasswordRequest request = change(" Campus#2026 ", "Nueva#2026x", "Nueva#2026x");

        assertEquals(" Campus#2026 ", request.getCurrentPassword());
    }

        /**
     * A field outside the contract, such as the email, the id or the status, is rejected and indicates which one it is
     * (RG-04)
     *
     * @throws Exception if reading the JSON fails
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
     * The names of the contract are read from the JSON
     *
     * @throws Exception if reading the JSON fails
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
