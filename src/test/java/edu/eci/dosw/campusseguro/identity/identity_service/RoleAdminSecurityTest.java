package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.RoleAdminController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleAdminService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * Seguridad HTTP de las rutas de administración de roles con la cadena de filtros real (RN-MS01-23): ninguna
 * es pública, sin cabeceras de identidad responden 401 con el ErrorResponse y con cabeceras llegan al
 * controlador, que decide por permisos
 */
@WebMvcTest(RoleAdminController.class)
@Import(SecurityConfig.class)
class RoleAdminSecurityTest {

    private static final String ADMIN_ID = "a0000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleAdminService roleAdminService;

    /**
     * Ninguna ruta de administración de roles es pública: sin cabeceras responde 401 y no llega al servicio
     *
     * @param method método HTTP
     * @param path ruta
     * @throws Exception si falla la petición simulada
     */
    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/roles/details",
            "GET,/api/v1/roles/MONITOR",
            "GET,/api/v1/permissions",
            "POST,/api/v1/roles",
            "PUT,/api/v1/roles/MONITOR",
            "PUT,/api/v1/roles/MONITOR/permissions",
            "DELETE,/api/v1/roles/MONITOR"
    })
    void roleAdminRoutes_withoutIdentityHeaders_return401(String method, String path) throws Exception {
        var request = switch (method) {
            case "POST" -> post(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "PUT" -> put(path).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "DELETE" -> delete(path);
            default -> get(path);
        };

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE))
                .andExpect(header().exists(CorrelationIds.HEADER));

        verifyNoInteractions(roleAdminService);
    }

    /**
     * Sin X-User-Permission la petición no tiene identidad y responde 401, aunque lleve el rol ADMIN
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void roleAdminRoutes_withoutThePermissionHeader_return401() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE));

        verifyNoInteractions(roleAdminService);
    }

    /**
     * Un código de permiso con formato inválido en X-User-Permission responde 401 con el motivo
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void roleAdminRoutes_withAnInvalidPermissionFormat_return401() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, "MS01:ROLE:MANAGE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.INVALID_PERMISSION_MESSAGE));

        verifyNoInteractions(roleAdminService);
    }

    /**
     * Con las tres cabeceras la petición llega al controlador
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void roleAdminRoutes_withIdentityHeaders_reachTheController() throws Exception {
        when(roleAdminService.listPermissions(any())).thenReturn(List.of(
                new PermissionItemResponse("ms01:profile:read", "Consultar el propio perfil")));

        mockMvc.perform(get("/api/v1/permissions")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .header(IdentityHeaderParser.USER_PERMISSION_HEADER, TestIdentities.permissionHeader("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].permiso").value("ms01:profile:read"));

        verify(roleAdminService).listPermissions(any());
    }

    /**
     * La ruta interna de cortes de sesión no la atiende esta cadena: sin la cadena interna sigue exigiendo
     * identidad de usuario
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void sessionCutsPath_isNotPublicInTheDefaultChain() throws Exception {
        mockMvc.perform(get("/api/v1/internal/session-cuts"))
                .andExpect(status().isUnauthorized());
    }
}
