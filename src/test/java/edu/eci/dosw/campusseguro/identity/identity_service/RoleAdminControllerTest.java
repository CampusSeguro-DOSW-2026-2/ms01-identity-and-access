package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.RoleAdminController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RoleDetailResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.BusinessRuleException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ConflictException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.RoleNotFoundException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleAdminService;

/**
 * RF-MS01-10: HTTP contract of the role administration and permission catalog routes, reading of the identity from the
 * gateway headers, status codes and translation of errors into the ErrorResponse
 */
@ExtendWith(MockitoExtension.class)
class RoleAdminControllerTest {

    private static final String ROLES_URL = "/api/v1/roles";
    private static final String DETAILS_URL = "/api/v1/roles/details";
    private static final String PERMISSIONS_URL = "/api/v1/permissions";
    private static final String ADMIN_ID = "a0000000-0000-4000-8000-000000000001";
    private static final String CORRELATION_ID = "8d3f6c58-9a35-4b1c-8f0e-2f6d3a1b7c90";

    @Mock
    private RoleAdminService roleAdminService;

    @InjectMocks
    private RoleAdminController controller;

    private MockMvc mockMvc;

        /**
     * Builds MockMvc with the controller, the global error handler and the identity resolver
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new RequesterIdentityArgumentResolver())
                .build();
    }

        /**
     * Sample role
     *
     * @return the detail of a custom role
     */
    private RoleDetailResponse monitor() {
        return new RoleDetailResponse("MONITOR_EVACUACION", "Apoya la evacuación",
                List.of("ms01:profile:read", "ms04:checkin:write"), false);
    }

        /**
     * Identity header of the administrator in a request
     *
     * @param builder request to which the headers are added
     * @return the same request with the identity of the ADMIN role
     */
    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-User-Id", ADMIN_ID)
                .header("X-User-Role", "ADMIN")
                .header("X-User-Permission", TestIdentities.permissionHeader("ADMIN"))
                .header("X-Correlation-ID", CORRELATION_ID);
    }

    // ------------------------------------------------------------------ queries

        /**
     * GET /roles/details: 200 with the roles, their permissions and the system flag, and the service receives the
     * identity built with the headers
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void listRoles_returns200WithTheDetailAndPassesTheIdentity() throws Exception {
        when(roleAdminService.listRoles(any())).thenReturn(List.of(
                new RoleDetailResponse("ADMIN", "Administrador", List.of("ms01:role:manage"), true), monitor()));

        mockMvc.perform(asAdmin(get(DETAILS_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rol").value("ADMIN"))
                .andExpect(jsonPath("$[0].es_sistema").value(true))
                .andExpect(jsonPath("$[1].rol").value("MONITOR_EVACUACION"))
                .andExpect(jsonPath("$[1].descripcion").value("Apoya la evacuación"))
                .andExpect(jsonPath("$[1].permisos[1]").value("ms04:checkin:write"))
                .andExpect(jsonPath("$[1].es_sistema").value(false));

        ArgumentCaptor<RequesterIdentity> captor = ArgumentCaptor.forClass(RequesterIdentity.class);
        verify(roleAdminService).listRoles(captor.capture());
        assertEquals(UUID.fromString(ADMIN_ID), captor.getValue().userId());
        assertEquals(Set.of("ADMIN"), captor.getValue().roles());
        assertEquals(TestIdentities.permissionsOf("ADMIN"), captor.getValue().permissions());
    }

        /**
     * GET /roles/details without headers: 401 and the service is not called
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void listRoles_withoutIdentityHeaders_returns401() throws Exception {
        mockMvc.perform(get(DETAILS_URL).header("X-Correlation-ID", CORRELATION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));

        verifyNoInteractions(roleAdminService);
    }

        /**
     * GET /roles/details without permission: 403 PROHIBIDO
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void listRoles_withoutPermission_returns403() throws Exception {
        when(roleAdminService.listRoles(any())).thenThrow(new ForbiddenOperationException(
                "No cuenta con los permisos suficientes para realizar esta operación."));

        mockMvc.perform(asAdmin(get(DETAILS_URL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.status").value(403));
    }

        /**
     * GET /roles/{rol}: 200 with the detail and the route reaches the service without normalizing
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void getRole_returns200AndPassesThePathValue() throws Exception {
        when(roleAdminService.getRole(any(), eq("monitor_evacuacion"))).thenReturn(monitor());

        mockMvc.perform(asAdmin(get(ROLES_URL + "/monitor_evacuacion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("MONITOR_EVACUACION"));
    }

        /**
     * GET /roles/details is resolved as a literal route and not as a role named details
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void getRole_detailsIsALiteralPathAndNotARoleName() throws Exception {
        when(roleAdminService.listRoles(any())).thenReturn(List.of());

        mockMvc.perform(asAdmin(get(DETAILS_URL)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(roleAdminService, never()).getRole(any(), any());
    }

        /**
     * GET /roles/{rol} with a non-existent role: 404 NO_ENCONTRADO with the message of the form
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void getRole_unknownRole_returns404() throws Exception {
        when(roleAdminService.getRole(any(), any())).thenThrow(new RoleNotFoundException("FANTASMA"));

        mockMvc.perform(asAdmin(get(ROLES_URL + "/FANTASMA")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Rol no encontrado."))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION_ID));
    }

        /**
     * GET /roles/{rol} with a name with an invalid format: 400 VALIDACION with the error of the rol field
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void getRole_invalidName_returns400WithTheFieldError() throws Exception {
        when(roleAdminService.getRole(any(), any())).thenThrow(new InvalidRequestException("Datos inválidos.",
                List.of(new ErrorResponse.ErrorDetail("rol", "Rol no válido."))));

        mockMvc.perform(asAdmin(get(ROLES_URL + "/rol-malo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("rol"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Rol no válido."));
    }

        /**
     * GET /permissions: 200 with the catalog
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void listPermissions_returns200WithTheCatalog() throws Exception {
        when(roleAdminService.listPermissions(any())).thenReturn(List.of(
                new PermissionItemResponse("ms01:password:change", "Cambiar la propia contraseña"),
                new PermissionItemResponse("ms01:profile:read", "Consultar el propio perfil")));

        mockMvc.perform(asAdmin(get(PERMISSIONS_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].permiso").value("ms01:password:change"))
                .andExpect(jsonPath("$[0].descripcion").value("Cambiar la propia contraseña"));
    }

        /**
     * GET /permissions without headers: 401
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void listPermissions_withoutIdentityHeaders_returns401() throws Exception {
        mockMvc.perform(get(PERMISSIONS_URL)).andExpect(status().isUnauthorized());

        verifyNoInteractions(roleAdminService);
    }

    // ------------------------------------------------------------------ creation

        /**
     * POST /roles: 201 with the role in the body and its path in Location, the service receives the body and the
     * correlation id
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void createRole_returns201WithLocationAndBody() throws Exception {
        when(roleAdminService.createRole(any(), any(), any())).thenReturn(monitor());

        mockMvc.perform(asAdmin(post(ROLES_URL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"rol": "monitor_evacuacion", "descripcion": "Apoya la evacuación",
                         "permisos": ["ms01:profile:read", "ms04:checkin:write"]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/roles/MONITOR_EVACUACION"))
                .andExpect(jsonPath("$.rol").value("MONITOR_EVACUACION"))
                .andExpect(jsonPath("$.es_sistema").value(false));

        ArgumentCaptor<CreateRoleRequest> request = ArgumentCaptor.forClass(CreateRoleRequest.class);
        verify(roleAdminService).createRole(any(RequesterIdentity.class), request.capture(), eq(CORRELATION_ID));
        assertEquals("monitor_evacuacion", request.getValue().getRol());
        assertEquals("Apoya la evacuación", request.getValue().getDescripcion());
        assertEquals(List.of("ms01:profile:read", "ms04:checkin:write"), request.getValue().getPermisos());
    }

        /**
     * POST /roles: a field outside the contract reaches the service as an unknown field so it is rejected with an error
     * per field
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void createRole_unknownField_isHandedToTheServiceAsUnknown() throws Exception {
        when(roleAdminService.createRole(any(), any(), any())).thenReturn(monitor());

        mockMvc.perform(asAdmin(post(ROLES_URL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\": \"X1\", \"descripcion\": \"abc\", \"permisos\": [], \"es_sistema\": true}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateRoleRequest> request = ArgumentCaptor.forClass(CreateRoleRequest.class);
        verify(roleAdminService).createRole(any(), request.capture(), any());
        assertEquals("es_sistema", request.getValue().unknownFieldErrors().get(0).campo());
    }

        /**
     * POST /roles: 400, 403, 409 and 422 from the service come out with their status and their code
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void createRole_serviceErrors_areTranslated() throws Exception {
        String body = "{\"rol\": \"X1\", \"descripcion\": \"abc\", \"permisos\": [\"ms01:profile:read\"]}";
        when(roleAdminService.createRole(any(), any(), any()))
                .thenThrow(new InvalidRequestException("Datos inválidos.",
                        List.of(new ErrorResponse.ErrorDetail("descripcion", "La descripción debe tener entre 3 y "
                                + "200 caracteres."))))
                .thenThrow(new ForbiddenOperationException("No cuenta con los permisos suficientes."))
                .thenThrow(new ConflictException("Ya existe un rol con ese nombre."))
                .thenThrow(new BusinessRuleException("El permiso ms01:role:manage es exclusivo del rol ADMIN."));

        mockMvc.perform(asAdmin(post(ROLES_URL)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("descripcion"));
        mockMvc.perform(asAdmin(post(ROLES_URL)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mockMvc.perform(asAdmin(post(ROLES_URL)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe un rol con ese nombre."));
        mockMvc.perform(asAdmin(post(ROLES_URL)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.mensaje").value("El permiso ms01:role:manage es exclusivo del rol ADMIN."));
    }

        /**
     * POST /roles with a body that is not JSON: 400 and the service is not called
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void createRole_malformedBody_returns400() throws Exception {
        mockMvc.perform(asAdmin(post(ROLES_URL)).contentType(MediaType.APPLICATION_JSON).content("{ no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(roleAdminService);
    }

        /**
     * POST /roles without identity headers: 401
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void createRole_withoutIdentityHeaders_returns401() throws Exception {
        mockMvc.perform(post(ROLES_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roleAdminService);
    }

    // ------------------------------------------------------------------ edition

        /**
     * PUT /roles/{rol}: 200 with the updated role, the service receives the path, the body and the correlation id
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void updateRole_returns200AndPassesPathBodyAndCorrelationId() throws Exception {
        when(roleAdminService.updateRole(any(), any(), any(), any())).thenReturn(monitor());

        mockMvc.perform(asAdmin(put(ROLES_URL + "/MONITOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\": \"MONITOR_EVACUACION\", \"descripcion\": \"Apoya la evacuación\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("MONITOR_EVACUACION"));

        ArgumentCaptor<UpdateRoleRequest> request = ArgumentCaptor.forClass(UpdateRoleRequest.class);
        verify(roleAdminService).updateRole(any(RequesterIdentity.class), eq("MONITOR"), request.capture(),
                eq(CORRELATION_ID));
        assertEquals("MONITOR_EVACUACION", request.getValue().getRol());
        assertEquals("Apoya la evacuación", request.getValue().getDescripcion());
    }

        /**
     * PUT /roles/{rol}: 404 and 409 from the service come out with their status and their code
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void updateRole_serviceErrors_areTranslated() throws Exception {
        String body = "{\"rol\": \"OTRO\", \"descripcion\": \"abc\"}";
        when(roleAdminService.updateRole(any(), any(), any(), any()))
                .thenThrow(new RoleNotFoundException("MONITOR"))
                .thenThrow(new ConflictException("Los roles de sistema no se pueden renombrar."));

        mockMvc.perform(asAdmin(put(ROLES_URL + "/MONITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mockMvc.perform(asAdmin(put(ROLES_URL + "/ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Los roles de sistema no se pueden renombrar."));
    }

    // ------------------------------------------------------------------ permissions

        /**
     * PUT /roles/{rol}/permissions: 200 with the updated role and the body reaches the service
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void updatePermissions_returns200AndPassesTheList() throws Exception {
        when(roleAdminService.updatePermissions(any(), any(), any(), any())).thenReturn(monitor());

        mockMvc.perform(asAdmin(put(ROLES_URL + "/MONITOR/permissions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permisos\": [\"ms01:profile:read\", \"ms04:checkin:write\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos.length()").value(2));

        ArgumentCaptor<UpdateRolePermissionsRequest> request =
                ArgumentCaptor.forClass(UpdateRolePermissionsRequest.class);
        verify(roleAdminService).updatePermissions(any(RequesterIdentity.class), eq("MONITOR"), request.capture(),
                eq(CORRELATION_ID));
        assertEquals(List.of("ms01:profile:read", "ms04:checkin:write"), request.getValue().getPermisos());
    }

        /**
     * PUT /roles/{rol}/permissions: 422 from the ms01:role:manage rule
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void updatePermissions_businessRule_returns422() throws Exception {
        when(roleAdminService.updatePermissions(any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException("El rol ADMIN debe conservar el permiso ms01:role:manage."));

        mockMvc.perform(asAdmin(put(ROLES_URL + "/ADMIN/permissions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permisos\": [\"ms01:profile:read\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
    }

    // ------------------------------------------------------------------ deletion

        /**
     * DELETE /roles/{rol}: 204 without a body and the service receives the path and the correlation id
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void deleteRole_returns204WithoutBody() throws Exception {
        mockMvc.perform(asAdmin(delete(ROLES_URL + "/MONITOR")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(roleAdminService).deleteRole(any(RequesterIdentity.class), eq("MONITOR"), eq(CORRELATION_ID));
    }

        /**
     * DELETE /roles/{rol}: 404 and 409 from the service come out with their status and their code
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void deleteRole_serviceErrors_areTranslated() throws Exception {
        doThrow(new RoleNotFoundException("FANTASMA"))
                .doThrow(new ConflictException("Los roles de sistema no se pueden eliminar."))
                .when(roleAdminService).deleteRole(any(), any(), any());

        mockMvc.perform(asAdmin(delete(ROLES_URL + "/FANTASMA")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Rol no encontrado."));
        mockMvc.perform(asAdmin(delete(ROLES_URL + "/ESTUDIANTE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

        /**
     * DELETE /roles/{rol} without identity headers: 401
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void deleteRole_withoutIdentityHeaders_returns401() throws Exception {
        mockMvc.perform(delete(ROLES_URL + "/MONITOR")).andExpect(status().isUnauthorized());

        verifyNoInteractions(roleAdminService);
    }

        /**
     * A method not defined on the routes answers 405
     *
     * @throws Exception if the simulated request fails
     */
    @Test
    void unsupportedMethods_return405() throws Exception {
        mockMvc.perform(asAdmin(delete(PERMISSIONS_URL)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
        mockMvc.perform(asAdmin(post(ROLES_URL + "/MONITOR")))
                .andExpect(status().isMethodNotAllowed());
    }
}
