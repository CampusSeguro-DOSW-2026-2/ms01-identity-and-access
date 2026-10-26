package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.IndividualChangeStateResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

/**
 * Pruebas de PATCH /api/v1/users/status (RF-MS01-07, formulario F-08)
 */
@ExtendWith(MockitoExtension.class)
class BulkUserStatusOperationControllerTest {

    private static final String URL = "/api/v1/users/status";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

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
     * Construye el cuerpo JSON del cambio de estado
     *
     * @param targetId identificador del usuario a modificar
     * @param estado estado solicitado
     * @return el cuerpo JSON de la petición
     */
    private String body(UUID targetId, String estado) {
        return "{\"ids\": [\"" + targetId + "\"], \"estado\": \"" + estado + "\"}";
    }

    /**
     * Un cambio de estado válido responde 200 con el estado anterior y el nuevo
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void changeUserStatus_validRequest_returns200WithPreviousAndNewState() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ChangeStatusResponse serviceResponse = ChangeStatusResponse.fromEntity(List.of(
                new IndividualChangeStateResponse(targetId, UserState.ACTIVO, UserState.INACTIVO)));
        when(userService.changeUserStatus(any(RequesterIdentity.class), any(ChangeStatusRequest.class),
                eq(CORRELATION_ID))).thenReturn(serviceResponse);

        mockMvc.perform(patch(URL)
                .header("X-User-Id", requesterId.toString())
                .header("X-User-Role", "ADMIN")
                .header("X-Correlation-ID", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(targetId, "INACTIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].id").value(targetId.toString()))
                .andExpect(jsonPath("$.resultados[0].estado_anterior").value("ACTIVO"))
                .andExpect(jsonPath("$.resultados[0].estado_nuevo").value("INACTIVO"));

        ArgumentCaptor<ChangeStatusRequest> captor = ArgumentCaptor.forClass(ChangeStatusRequest.class);
        verify(userService).changeUserStatus(any(RequesterIdentity.class), captor.capture(), eq(CORRELATION_ID));
        assertEquals(List.of(targetId.toString()), captor.getValue().getIds());
        assertEquals("INACTIVO", captor.getValue().getEstado());
    }

    /**
     * Un solicitante sin permiso recibe 403
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void changeUserStatus_withoutPermission_returns403() throws Exception {
        when(userService.changeUserStatus(any(RequesterIdentity.class), any(ChangeStatusRequest.class), any()))
                .thenThrow(new ForbiddenOperationException("No tiene permisos para realizar esta operación."));

        mockMvc.perform(patch(URL)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "AUDITOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), "INACTIVO")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    /**
     * Modificar el propio estado responde 403 con el mensaje del formulario
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void changeUserStatus_selfModification_returns403WithTheFormMessage() throws Exception {
        UUID requesterId = UUID.randomUUID();
        when(userService.changeUserStatus(any(RequesterIdentity.class), any(ChangeStatusRequest.class), any()))
                .thenThrow(new ForbiddenOperationException("No puede cambiar su propio estado."));

        mockMvc.perform(patch(URL)
                .header("X-User-Id", requesterId.toString())
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(requesterId, "INACTIVO")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No puede cambiar su propio estado."));
    }

    /**
     * Un usuario inexistente responde 404
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void changeUserStatus_userNotFound_returns404() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(userService.changeUserStatus(any(RequesterIdentity.class), any(ChangeStatusRequest.class), any()))
                .thenThrow(new UsersNotFoundException(List.of(targetId)));

        mockMvc.perform(patch(URL)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(targetId, "INACTIVO")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Usuario no encontrado."));
    }

    /**
     * Sin cabeceras de identidad la respuesta es 401
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void changeUserStatus_withoutIdentityHeaders_returns401() throws Exception {
        mockMvc.perform(patch(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), "INACTIVO")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(userService);
    }

    /**
     * Un cuerpo mal formado responde 400
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void changeUserStatus_malformedBody_returns400() throws Exception {
        mockMvc.perform(patch(URL)
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[1, 2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(userService);
    }
}
