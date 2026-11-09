package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.UserController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UserNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

/**
 * Pruebas de los endpoints de perfil propio y consulta por id (RF-MS01-06, RF-MS01-07)
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String URL = "/api/v1/users";
    private static final String ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    /**
     * Prepara el controlador y el cliente de pruebas con el manejador global de errores y la resolución de identidad
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new RequesterIdentityArgumentResolver())
                .build();
    }

    /**
     * Construye una respuesta de perfil de ejemplo
     *
     * @param id identificador del usuario
     * @param firstName primer nombre
     * @param lastName primer apellido
     * @return el perfil de ejemplo
     */
    private UserProfileResponse profile(UUID id, String firstName, String lastName) {
        return new UserProfileResponse(id, firstName, null, lastName, null, "carlos.gomez@escuelaing.edu.co",
                List.of(UserRole.ESTUDIANTE), UserState.ACTIVO);
    }

    /**
     * La consulta del propio perfil responde 200 con los nombres del contrato y sin nombres opcionales nulos
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void getMyProfile_existingUser_returns200WithWireNamesAndWithoutNullOptionalNames() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getMyProfile(any(RequesterIdentity.class))).thenReturn(profile(id, "Carlos", "Gomez"));

        mockMvc.perform(get(URL + "/me")
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.correo").value("carlos.gomez@escuelaing.edu.co"))
                .andExpect(jsonPath("$.primer_nombre").value("Carlos"))
                .andExpect(jsonPath("$.primer_apellido").value("Gomez"))
                .andExpect(jsonPath("$.segundo_nombre").doesNotExist())
                .andExpect(jsonPath("$.segundo_apellido").doesNotExist())
                .andExpect(jsonPath("$.roles[0]").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    /**
     * La identidad del solicitante se construye con las cabeceras del gateway
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void getMyProfile_buildsTheIdentityFromTheGatewayHeaders() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getMyProfile(any(RequesterIdentity.class))).thenReturn(profile(id, "Carlos", "Gomez"));

        mockMvc.perform(get(URL + "/me")
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "PROFESOR,BRIGADISTA"))
                .andExpect(status().isOk());

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(userService).getMyProfile(captor.capture());
        assertEquals(id, captor.getValue().userId());
        assertEquals(EnumSet.of(UserRole.PROFESOR, UserRole.BRIGADISTA), captor.getValue().roles());
    }

    /**
     * Sin cabeceras de identidad la respuesta es 401 y no se llama al servicio
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void getMyProfile_withoutIdentityHeaders_returns401AndDoesNotCallTheService() throws Exception {
        mockMvc.perform(get(URL + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.errores").isArray())
                .andExpect(jsonPath("$.correlation_id").exists());

        verifyNoInteractions(userService);
    }

    /**
     * Un usuario inexistente responde 404
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void getMyProfile_unknownUser_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getMyProfile(any(RequesterIdentity.class))).thenThrow(new UserNotFoundException(id));

        mockMvc.perform(get(URL + "/me")
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Usuario no encontrado."));
    }

    /**
     * Una actualización válida del perfil responde 200
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void updateMyProfile_validRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.updateProfile(any(RequesterIdentity.class), any(UserUpdateProfileRequest.class)))
                .thenReturn(profile(id, "Sebastian", "Perez"));

        String jsonBody = "{\"primer_nombre\": \"Sebastian\", \"primer_apellido\": \"Perez\"}";

        mockMvc.perform(put(URL + "/me")
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "ESTUDIANTE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primer_nombre").value("Sebastian"))
                .andExpect(jsonPath("$.primer_apellido").value("Perez"));

        ArgumentCaptor<UserUpdateProfileRequest> captor = ArgumentCaptor.forClass(UserUpdateProfileRequest.class);
        verify(userService).updateProfile(any(RequesterIdentity.class), captor.capture());
        assertEquals("Sebastian", captor.getValue().getFirstName());
        assertEquals("Perez", captor.getValue().getFirstLastName());
    }

    /**
     * Los campos desconocidos se capturan para que el servicio los rechace
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void updateMyProfile_unknownFields_areCapturedForTheServiceToReject() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.updateProfile(any(RequesterIdentity.class), any(UserUpdateProfileRequest.class)))
                .thenThrow(new InvalidRequestException("Datos inválidos.", List.of(
                        new ErrorResponse.ErrorDetail("correo", "El correo no se puede modificar."))));

        String jsonBody = "{\"primer_nombre\": \"Sebastian\", \"primer_apellido\": \"Perez\","
                + " \"correo\": \"otro@escuelaing.edu.co\"}";

        mockMvc.perform(put(URL + "/me")
                .header(ID_HEADER, id.toString())
                .header(ROLE_HEADER, "ESTUDIANTE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El correo no se puede modificar."));

        ArgumentCaptor<UserUpdateProfileRequest> captor = ArgumentCaptor.forClass(UserUpdateProfileRequest.class);
        verify(userService).updateProfile(any(RequesterIdentity.class), captor.capture());
        assertTrue(captor.getValue().unknownFieldNames().contains("correo"));
    }

    /**
     * Un JSON mal formado responde 400 sin llamar al servicio
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void updateMyProfile_malformedJson_returns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(put(URL + "/me")
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Datos inválidos."));

        verifyNoInteractions(userService);
    }

    /**
     * La consulta de un usuario por id con permiso responde 200
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void getUserById_withPermission_returns200() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userService.getUserById(any(RequesterIdentity.class), eq(targetId)))
                .thenReturn(profile(targetId, "Carlos", "Gomez"));

        mockMvc.perform(get(URL + "/" + targetId)
                .header(ID_HEADER, requesterId.toString())
                .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId.toString()));
    }

    /**
     * La consulta de un usuario por id sin permiso responde 403
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void getUserById_withoutPermission_returns403() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(userService.getUserById(any(RequesterIdentity.class), eq(targetId)))
                .thenThrow(new ForbiddenOperationException("No tiene permisos para realizar esta operación."));

        mockMvc.perform(get(URL + "/" + targetId)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.mensaje").value("No tiene permisos para realizar esta operación."));
    }

    /**
     * Un id con formato inválido responde 400
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void getUserById_idWithInvalidFormat_returns400() throws Exception {
        mockMvc.perform(get(URL + "/no-es-un-uuid")
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(userService);
    }
}
