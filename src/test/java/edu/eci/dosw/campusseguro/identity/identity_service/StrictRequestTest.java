package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.LoginRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;

/**
 * Rechazo de campos que no están en el contrato (RG-04): se registran al leer el JSON y cada uno se
 * informa como un error por campo
 */
class StrictRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Los campos desconocidos se registran sin que falle la lectura del JSON
     *
     * @throws Exception si falla la lectura del JSON
     */
    @Test
    void jsonWithUnknownFields_registersThemWithoutFailingTheRead() throws Exception {
        LoginRequest request = mapper.readValue(
                "{\"correo\":\"a@escuelaing.edu.co\",\"contrasena\":\"x\",\"rol\":\"ADMIN\",\"otro\":1}",
                LoginRequest.class);

        assertEquals(java.util.Set.of("rol", "otro"), request.unknownFieldNames());
        assertEquals("a@escuelaing.edu.co", request.getCorreo());
    }

    /**
     * Un JSON sin campos desconocidos no registra nada
     *
     * @throws Exception si falla la lectura del JSON
     */
    @Test
    void jsonWithoutUnknownFields_registersNothing() throws Exception {
        LoginRequest request = mapper.readValue("{\"correo\":\"a@escuelaing.edu.co\",\"contrasena\":\"x\"}",
                LoginRequest.class);

        assertTrue(request.unknownFieldNames().isEmpty());
        assertTrue(request.unknownFieldErrors().isEmpty());
    }

    /**
     * Cada campo desconocido produce un error con el mensaje genérico
     */
    @Test
    void unknownFieldErrors_haveOneEntryPerFieldWithTheGenericMessage() {
        LoginRequest request = new LoginRequest();
        request.registerUnknownField("rol", "ADMIN");
        request.registerUnknownField("estado", "ACTIVO");

        assertEquals(List.of(
                new ErrorResponse.ErrorDetail("rol", "El campo no está permitido."),
                new ErrorResponse.ErrorDetail("estado", "El campo no está permitido.")),
                request.unknownFieldErrors());
    }

    /**
     * Los nombres de campo inseguros se reemplazan por un marcador para no devolverlos
     */
    @Test
    void unsafeFieldNames_areReplacedByAPlaceholderSoTheyAreNeverEchoed() {
        LoginRequest request = new LoginRequest();
        request.registerUnknownField("<script>alert(1)</script>", "x");
        request.registerUnknownField("a".repeat(41), "x");

        List<ErrorResponse.ErrorDetail> errors = request.unknownFieldErrors();

        assertEquals(2, errors.size());
        assertTrue(errors.stream().allMatch(error -> "campo_desconocido".equals(error.campo())));
    }

    /**
     * La petición de perfil usa los mensajes del formulario para los campos rechazados
     */
    @Test
    void profileRequest_usesTheFormMessagesForTheRejectedFields() {
        UserUpdateProfileRequest request = new UserUpdateProfileRequest();
        request.registerUnknownField("correo", "x");
        request.registerUnknownField("roles", "x");
        request.registerUnknownField("estado", "x");
        request.registerUnknownField("password", "x");

        List<ErrorResponse.ErrorDetail> errors = request.unknownFieldErrors();

        assertEquals("El correo no se puede modificar.", errors.get(0).mensaje());
        assertEquals("Los roles no se pueden modificar desde el perfil.", errors.get(1).mensaje());
        assertEquals("El estado no se puede modificar desde el perfil.", errors.get(2).mensaje());
        assertEquals("El campo no está permitido.", errors.get(3).mensaje());
    }

    /**
     * La colección de campos desconocidos no se puede modificar desde fuera
     */
    @Test
    void unknownFieldNames_isReadOnly() {
        LoginRequest request = new LoginRequest();

        assertThrows(UnsupportedOperationException.class, () -> request.unknownFieldNames().add("x"));
    }

    /**
     * La petición de cambio de roles también registra los campos desconocidos
     *
     * @throws Exception si falla la lectura del JSON
     */
    @Test
    void changeRolesRequest_alsoRegistersUnknownFields() throws Exception {
        ChangeRolesRequest request = mapper.readValue("{\"ids\":[],\"solicitante\":\"x\"}",
                ChangeRolesRequest.class);

        assertTrue(request.unknownFieldNames().contains("solicitante"));
    }
}
