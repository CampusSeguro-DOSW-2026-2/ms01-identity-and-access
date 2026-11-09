package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.identity.identity_service.config.InternalSecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.identity.identity_service.controller.UserController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.BatchUsersResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.InternalServiceAuthenticationFilter;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

/**
 * Seguridad HTTP de las rutas internas con las dos cadenas reales (RF-MS01-09): se autorizan por
 * X-Service-Name y no cambian la seguridad de las rutas protegidas
 */
@WebMvcTest(UserController.class)
@Import({InternalSecurityConfig.class, SecurityConfig.class})
class InternalSecurityTest {

    private static final String SERVICE_HEADER = InternalServiceAuthenticationFilter.SERVICE_NAME_HEADER;
    private static final String BATCH_URL = "/api/v1/users/batch";
    private static final String BY_ROLE_URL = "/api/v1/users/by-role";
    private static final UUID USER_ID = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID MISSING_ID = UUID.fromString("9b2e7c41-0d3a-4f6b-8e15-6a4c2d9f0b73");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    /**
     * Construye el perfil de ejemplo sin segundo nombre ni segundo apellido
     *
     * @return el perfil de ejemplo
     */
    private static UserProfileResponse profile() {
        return new UserProfileResponse(USER_ID, "María", null, "Pérez", null, "maria.perez@escuelaing.edu.co",
                List.of(UserRole.BRIGADISTA, UserRole.ESTUDIANTE), UserState.ACTIVO);
    }

    /**
     * Cuerpo del batch con un id
     *
     * @return el JSON del cuerpo
     */
    private static String batchBody() {
        return "{\"ids\":[\"" + USER_ID + "\"]}";
    }

    /**
     * Un servicio autorizado recibe el batch con los nombres de campo del contrato
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void batch_fromAllowedService_returns200WithTheContractFields() throws Exception {
        when(userService.findUsersByIds(any(BatchUsersRequest.class)))
                .thenReturn(new BatchUsersResponse(List.of(profile()), List.of(MISSING_ID)));

        mockMvc.perform(post(BATCH_URL).header(SERVICE_HEADER, "ms03")
                .contentType(MediaType.APPLICATION_JSON).content(batchBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.usuarios[0].segundo_nombre").doesNotExist())
                .andExpect(jsonPath("$.usuarios[0].roles[0]").value("BRIGADISTA"))
                .andExpect(jsonPath("$.no_encontrados[0]").value(MISSING_ID.toString()));
    }

    /**
     * Sin X-Service-Name el batch responde 401 con el motivo y no llega al servicio
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void batch_withoutServiceName_returns401WithTheReason() throws Exception {
        mockMvc.perform(post(BATCH_URL).contentType(MediaType.APPLICATION_JSON).content(batchBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(InternalServiceAuthenticationFilter.MISSING_MESSAGE))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());

        verifyNoInteractions(userService);
    }

    /**
     * Las cabeceras de usuario no sustituyen a X-Service-Name en las rutas internas
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void batch_withUserHeadersOnly_returns401() throws Exception {
        mockMvc.perform(post(BATCH_URL)
                .header(IdentityHeaderParser.USER_ID_HEADER, USER_ID.toString())
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(batchBody()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    /**
     * Un servicio no autorizado recibe 403
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void batch_fromServiceNotAllowed_returns403() throws Exception {
        mockMvc.perform(post(BATCH_URL).header(SERVICE_HEADER, "ms04")
                .contentType(MediaType.APPLICATION_JSON).content(batchBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(userService);
    }

    /**
     * La consulta por rol llega a su método y no a /{id}
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void byRole_isResolvedAsALiteralPathAndNotAsAnId() throws Exception {
        when(userService.searchByRole(eq("BRIGADISTA"), eq("2026-11-07T15:04:05Z"), eq("0"), eq("100")))
                .thenReturn(new PageResponse<>(List.of(profile()), 0, 100, 1, 1));

        mockMvc.perform(get(BY_ROLE_URL).header(SERVICE_HEADER, "ms05")
                .param("rol", "BRIGADISTA").param("creado_antes", "2026-11-07T15:04:05Z")
                .param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.total_paginas").value(1));

        verify(userService).searchByRole(eq("BRIGADISTA"), eq("2026-11-07T15:04:05Z"), eq("0"), eq("100"));
    }

    /**
     * Sin parámetros opcionales la consulta por rol los envía como null al servicio
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void byRole_withoutOptionalParameters_passesNull() throws Exception {
        when(userService.searchByRole(eq("ESTUDIANTE"), isNull(), isNull(), isNull()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(BY_ROLE_URL).header(SERVICE_HEADER, "ms03").param("rol", "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty());
    }

    /**
     * Sin X-Service-Name la consulta por rol responde 401
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void byRole_withoutServiceName_returns401() throws Exception {
        mockMvc.perform(get(BY_ROLE_URL).param("rol", "BRIGADISTA"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(InternalServiceAuthenticationFilter.MISSING_MESSAGE));

        verifyNoInteractions(userService);
    }

    /**
     * Las rutas protegidas no cambian: GET /users/{id} sigue exigiendo las cabeceras de usuario aunque
     * llegue X-Service-Name
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void protectedUserById_withServiceNameOnly_stillReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + USER_ID).header(SERVICE_HEADER, "ms03"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaderParser.MISSING_MESSAGE));

        verifyNoInteractions(userService);
    }

    /**
     * Las rutas protegidas no cambian: GET /users/{id} con cabeceras de usuario llega al controlador
     *
     * @throws Exception si falla la petición simulada
     */
    @Test
    void protectedUserById_withUserHeaders_reachesTheController() throws Exception {
        when(userService.getUserById(any(), eq(USER_ID))).thenReturn(profile());

        mockMvc.perform(get("/api/v1/users/" + USER_ID)
                .header(IdentityHeaderParser.USER_ID_HEADER, UUID.randomUUID().toString())
                .header(IdentityHeaderParser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }
}
