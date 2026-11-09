package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.RoleController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;

/**
 * Seguridad HTTP con la cadena de filtros real (estandares de integracion 1.3 y 2.3, RN-MS01-23): solo son
 * publicas las rutas exactas del estandar, el resto exige las cabeceras de identidad y responde 401 con el
 * ErrorResponse, y el X-Correlation-ID se devuelve siempre
 */
@WebMvcTest(RoleController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final String ADMIN_ID = "a0000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    /**
     * Una ruta protegida sin cabeceras responde 401 con el cuerpo normalizado
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void protectedRoute_withoutIdentityHeaders_returns401WithErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());

        verifyNoInteractions(roleService);
    }

    /**
     * El correlation id recibido se conserva en la respuesta 401, tanto en el header como en el cuerpo
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void protectedRoute_withCorrelationId_returnsTheSameValueInHeaderAndBody() throws Exception {
        mockMvc.perform(get("/api/v1/roles").header(CorrelationIds.HEADER, "c19b472d-7a2a-4d3b-a12b-abcdef123456"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIds.HEADER, "c19b472d-7a2a-4d3b-a12b-abcdef123456"))
                .andExpect(jsonPath("$.correlation_id").value("c19b472d-7a2a-4d3b-a12b-abcdef123456"));
    }

    /**
     * Sin correlation id en la peticion se genera uno y el header y el cuerpo llevan el mismo valor
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void protectedRoute_withoutCorrelationId_generatesOneForHeaderAndBody() throws Exception {
        String generated = mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(CorrelationIds.HEADER))
                .andReturn().getResponse().getHeader(CorrelationIds.HEADER);

        mockMvc.perform(get("/api/v1/roles").header(CorrelationIds.HEADER, generated))
                .andExpect(jsonPath("$.correlation_id").value(generated));
    }

    /**
     * Un rol fuera del catalogo en X-User-Role responde 401 con el motivo
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void protectedRoute_withUnknownRole_returns401WithTheReason() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "SUPERUSUARIO"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.INVALID_ROLE_MESSAGE));
    }

    /**
     * Con cabeceras validas la peticion llega al controlador
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void protectedRoute_withIdentityHeaders_reachesTheController() throws Exception {
        when(roleService.getRoles(any())).thenReturn(List.of(
                new RolePermissionsResponse(UserRole.ESTUDIANTE, List.of("ms01:profile:read"))));

        mockMvc.perform(get("/api/v1/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIds.HEADER))
                .andExpect(jsonPath("$[0].rol").value("ESTUDIANTE"));
    }

    /**
     * El cambio de roles sin cabeceras responde 401 y no llega al servicio
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void changeRoles_withoutIdentityHeaders_returns401AndDoesNotReachTheService() throws Exception {
        mockMvc.perform(patch("/api/v1/users/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"5b1f0c2e-8d4a-4c1b-9a77-3f2e6d1a0b9c\"],\"agregar\":[\"PROFESOR\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(roleService);
    }

    /**
     * Las cuatro rutas publicas del estandar pasan la seguridad sin cabeceras, en este test no existe su
     * controlador, por eso la respuesta es el 404 normalizado y no un 401
     *
     * @param path ruta publica del estandar
     * @throws Exception si falla la peticion simulada
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset" })
    void publicRoutes_doNotRequireIdentity(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    /**
     * Las demas rutas bajo /auth no son publicas, el estandar prohibe los comodines publicos
     *
     * @param path ruta que no esta en la lista publica
     * @throws Exception si falla la peticion simulada
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/logout",
            "/api/v1/auth/otra",
            "/api/v1/auth/password/otra" })
    void otherAuthRoutes_areNotPublic(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /**
     * Una ruta publica solo lo es con su metodo exacto, GET /auth/login exige identidad
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void publicRoute_withAnotherMethod_isNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Las rutas de usuarios ya no son publicas
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void usersRoutes_areNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Con identidad valida una ruta inexistente responde el 404 normalizado
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void unknownRoute_withIdentity_returnsTheNormalized404() throws Exception {
        mockMvc.perform(get("/api/v1/inexistente")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());
    }

    /**
     * Con identidad valida un metodo no admitido responde el 405 normalizado
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void wrongMethod_withIdentity_returnsTheNormalized405() throws Exception {
        mockMvc.perform(get("/api/v1/users/roles")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "PATCH"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    /**
     * El health check es público para el Gateway y los orquestadores, en este test no existe el endpoint, por
     * eso la respuesta es el 404 normalizado y no un 401
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    /**
     * Los demás endpoints de actuator no son públicos
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void otherActuatorEndpoints_areNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Swagger solo es público en el desarrollo local, por defecto exige identidad
     *
     * @param path ruta de la documentación interactiva
     * @throws Exception si falla la peticion simulada
     */
    @ParameterizedTest
    @ValueSource(strings = { "/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs" })
    void swaggerRoutes_areNotPublicByDefault(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    /**
     * No se permite CORS en el microservicio, lo gestiona el Gateway
     *
     * @throws Exception si falla la peticion simulada
     */
    @Test
    void cors_isNotConfiguredInTheMicroservice() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                .header("Origin", "http://localhost:3000")
                .header(IdentityHeaderParser.USER_ID_HEADER, ADMIN_ID)
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
