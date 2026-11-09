package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import edu.eci.dosw.campusseguro.identity.identity_service.controller.RoleController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.UsersNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleService;

/**
 * GET /api/v1/roles y PATCH /api/v1/users/roles (RF-MS01-05, formulario F-09): contrato HTTP segun el
 * OpenAPI, lectura de la identidad desde las cabeceras del Gateway y traduccion de errores 400, 401, 403 y 404
 * al formato ErrorResponse
 */
@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    private static final String ROLES_URL = "/api/v1/roles";
    private static final String CHANGE_URL = "/api/v1/users/roles";
    private static final String ADMIN_ID = "a0000000-0000-4000-8000-000000000001";
    private static final String TARGET_ID = "5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c";

    @Mock
    private RoleService roleService;

    @InjectMocks
    private RoleController roleController;

    private MockMvc mockMvc;

    /**
     * Arma MockMvc con el controlador y el manejador global de errores
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(roleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new RequesterIdentityArgumentResolver())
                .build();
    }

    /**
     * Cuerpo valido de PATCH /users/roles
     *
     * @return JSON con un usuario, un rol a agregar y uno a retirar
     */
    private String validBody() {
        return """
                {
                  "ids": ["%s"],
                  "agregar": ["PROFESOR"],
                  "retirar": ["ESTUDIANTE"]
                }
                """.formatted(TARGET_ID);
    }

    // ------------------------------------------------------------------ GET /roles

    /**
     * 200: el servicio recibe la identidad armada con las cabeceras y la lista se serializa con rol y
     * permisos
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void getRoles_withIdentityHeaders_returns200AndPassesTheIdentity() throws Exception {
        when(roleService.getRoles(any(RequesterIdentity.class))).thenReturn(List.of(
                new RolePermissionsResponse(UserRole.ESTUDIANTE,
                        List.of("ms01:profile:read", "ms01:profile:write")),
                new RolePermissionsResponse(UserRole.PROFESOR, List.of("ms05:incident:report"))));

        mockMvc.perform(get(ROLES_URL)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ESTUDIANTE, PROFESOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rol").value("ESTUDIANTE"))
                .andExpect(jsonPath("$[0].permisos[0]").value("ms01:profile:read"))
                .andExpect(jsonPath("$[1].rol").value("PROFESOR"))
                .andExpect(jsonPath("$[1].permisos[0]").value("ms05:incident:report"));

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(roleService).getRoles(captor.capture());
        assertEquals(UUID.fromString(ADMIN_ID), captor.getValue().userId());
        assertEquals(EnumSet.of(UserRole.ESTUDIANTE, UserRole.PROFESOR), captor.getValue().roles());
    }

    /**
     * 401: sin cabeceras de identidad no se llama al servicio y el cuerpo sigue el formato ErrorResponse
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void getRoles_withoutIdentityHeaders_returns401() throws Exception {
        mockMvc.perform(get(ROLES_URL).header("X-Correlation-ID", "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Faltan las cabeceras de identidad (X-User-Id / X-User-Role)."))
                .andExpect(jsonPath("$.correlation_id").value("8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90"));

        verifyNoInteractions(roleService);
    }

    /**
     * 401: un rol fuera del catalogo en X-User-Role no establece una identidad
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void getRoles_withUnknownRole_returns401() throws Exception {
        mockMvc.perform(get(ROLES_URL)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "SUPERUSUARIO"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(roleService);
    }

    /**
     * 401: un X-User-Id que no es un UUID no establece una identidad
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void getRoles_withInvalidUserId_returns401() throws Exception {
        mockMvc.perform(get(ROLES_URL)
                .header("X-User-Id", "USR-1034")
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roleService);
    }

    // ------------------------------------------------------------------ PATCH /users/roles

    /**
     * 200: el servicio recibe el cuerpo, la identidad y el correlation id, y la respuesta lleva los roles
     * resultantes por usuario
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_validRequest_returns200WithTheResultingRoles() throws Exception {
        when(roleService.changeRoles(any(RequesterIdentity.class), any(ChangeRolesRequest.class),
                eq("8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90")))
                .thenReturn(new ChangeRolesResponse(List.of(
                        new ChangeRolesResponse.UserRoles(TARGET_ID, List.of(UserRole.PROFESOR)))));

        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .header("X-Correlation-ID", "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90")
                .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados.length()").value(1))
                .andExpect(jsonPath("$.resultados[0].id").value(TARGET_ID))
                .andExpect(jsonPath("$.resultados[0].roles[0]").value("PROFESOR"));

        ArgumentCaptor<RequesterIdentity> identity = ArgumentCaptor.forClass(RequesterIdentity.class);
        ArgumentCaptor<ChangeRolesRequest> body = ArgumentCaptor.forClass(ChangeRolesRequest.class);
        verify(roleService).changeRoles(identity.capture(), body.capture(), eq("8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90"));
        assertEquals(UUID.fromString(ADMIN_ID), identity.getValue().userId());
        assertEquals(EnumSet.of(UserRole.ADMIN), identity.getValue().roles());
        assertEquals(List.of(TARGET_ID), body.getValue().getIds());
        assertEquals(List.of("PROFESOR"), body.getValue().getAgregar());
        assertEquals(List.of("ESTUDIANTE"), body.getValue().getRetirar());
    }

    /**
     * Sin X-Correlation-ID el controlador pasa al servicio un UUID v4 generado
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_withoutCorrelationId_passesAGeneratedUuid() throws Exception {
        when(roleService.changeRoles(any(RequesterIdentity.class), any(ChangeRolesRequest.class), any()))
                .thenReturn(new ChangeRolesResponse(List.of()));

        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .content(validBody()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> correlation = ArgumentCaptor.forClass(String.class);
        verify(roleService).changeRoles(any(RequesterIdentity.class), any(ChangeRolesRequest.class),
                correlation.capture());
        assertEquals(4, UUID.fromString(correlation.getValue()).version());
    }

    /**
     * 401: sin cabeceras de identidad no se llama al servicio
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_withoutIdentityHeaders_returns401() throws Exception {
        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(roleService);
    }

    /**
     * RG-04: un campo que no está en el contrato llega al servicio registrado como desconocido para que lo
     * rechace con un error por campo, por ejemplo intentar enviar quien solicita en el cuerpo
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_unknownField_isCapturedForTheServiceToReject() throws Exception {
        when(roleService.changeRoles(any(RequesterIdentity.class), any(ChangeRolesRequest.class), any()))
                .thenReturn(new ChangeRolesResponse(List.of()));
        String body = """
                {
                  "ids": ["%s"],
                  "agregar": ["PROFESOR"],
                  "solicitante": "%s"
                }
                """.formatted(TARGET_ID, ADMIN_ID);

        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<ChangeRolesRequest> captured = ArgumentCaptor.forClass(ChangeRolesRequest.class);
        verify(roleService).changeRoles(any(RequesterIdentity.class), captured.capture(), any());
        assertTrue(captured.getValue().unknownFieldNames().contains("solicitante"));
    }

    /**
     * 400: un cuerpo que no es JSON valido o no es un objeto se rechaza
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_malformedBody_returns400() throws Exception {
        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .content("{\"ids\": \"no-es-un-arreglo\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(roleService);
    }

    /**
     * 400: sin cuerpo la peticion se rechaza
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_withoutBody_returns400() throws Exception {
        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(roleService);
    }

    /**
     * 400: las validaciones de F-09 se devuelven con su mensaje y el detalle por campo
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_validationFailure_returns400WithFieldDetail() throws Exception {
        when(roleService.changeRoles(any(RequesterIdentity.class), any(ChangeRolesRequest.class), any()))
                .thenThrow(new InvalidRequestException("Datos inválidos.",
                        List.of(new ErrorResponse.ErrorDetail("agregar", "Seleccione roles válidos."))));

        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .header("X-Correlation-ID", "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90")
                .content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Datos inválidos."))
                .andExpect(jsonPath("$.errores[0].campo").value("agregar"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Seleccione roles válidos."))
                .andExpect(jsonPath("$.correlation_id").value("8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90"));
    }

    /**
     * 400: un mensaje de regla sin campo (usuario sin roles) llega como mensaje con errores vacios
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_ruleFailureWithoutField_returns400WithMessage() throws Exception {
        when(roleService.changeRoles(any(RequesterIdentity.class), any(ChangeRolesRequest.class), any()))
                .thenThrow(new InvalidRequestException("Cada usuario debe conservar al menos un rol.", List.of()));

        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Cada usuario debe conservar al menos un rol."))
                .andExpect(jsonPath("$.errores.length()").value(0));
    }

    /**
     * 403: no ser administrador o modificar los propios roles responde PROHIBIDO
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_forbidden_returns403() throws Exception {
        when(roleService.changeRoles(any(RequesterIdentity.class), any(ChangeRolesRequest.class), any()))
                .thenThrow(new ForbiddenOperationException("No puede modificar sus propios roles."));

        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .header("X-Correlation-ID", "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90")
                .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.mensaje").value("No puede modificar sus propios roles."))
                .andExpect(jsonPath("$.correlation_id").value("8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90"));
    }

    /**
     * 404: un usuario inexistente responde NO_ENCONTRADO con el mensaje del formulario F-09
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_unknownUser_returns404() throws Exception {
        when(roleService.changeRoles(any(RequesterIdentity.class), any(ChangeRolesRequest.class), any()))
                .thenThrow(new UsersNotFoundException(List.of(UUID.fromString(TARGET_ID))));

        mockMvc.perform(patch(CHANGE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Usuario no encontrado."));
    }
}
