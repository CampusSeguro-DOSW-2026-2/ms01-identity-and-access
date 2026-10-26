package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.UserController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.InvalidRequestException;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentityArgumentResolver;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

/**
 * Pruebas de GET /api/v1/users (RF-MS01-07, formulario F-07)
 */
@ExtendWith(MockitoExtension.class)
class UserListControllerTest {

    private static final String URL = "/api/v1/users";
    private static final String ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String ADMIN_ROLE = "ADMIN";

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
     * @param email correo del usuario
     * @return el perfil de ejemplo
     */
    private UserProfileResponse response(String email) {
        return new UserProfileResponse(UUID.randomUUID(), "Ana", null, "Perez", null, email,
                List.of(UserRole.ESTUDIANTE), UserState.ACTIVO);
    }

    /**
     * El listado responde 200 con los nombres de paginación del contrato
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void list_asAdmin_returns200WithThePaginationWireNames() throws Exception {
        when(userService.search(any(RequesterIdentity.class), eq("ana"), eq("ACTIVO"), eq("ESTUDIANTE"), isNull(),
                isNull()))
                .thenReturn(new PageResponse<>(List.of(response("ana@escuelaing.edu.co")), 0, 20, 1, 1));

        mockMvc.perform(get(URL)
                .header(ID_HEADER, ADMIN_ID.toString())
                .header(ROLE_HEADER, ADMIN_ROLE)
                .param("correo", "ana")
                .param("estado", "ACTIVO")
                .param("rol", "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].correo").value("ana@escuelaing.edu.co"))
                .andExpect(jsonPath("$.contenido[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$.contenido[0].primer_nombre").value("Ana"))
                .andExpect(jsonPath("$.contenido[0].password").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.total_paginas").value(1));
    }

    /**
     * Sin parámetros se entregan nulos para que el servicio aplique los valores por defecto
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void list_withoutParams_passesNullsSoTheServiceAppliesTheDefaults() throws Exception {
        when(userService.search(any(RequesterIdentity.class), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(URL)
                .header(ID_HEADER, ADMIN_ID.toString())
                .header(ROLE_HEADER, ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0));

        verify(userService).search(any(RequesterIdentity.class), isNull(), isNull(), isNull(), isNull(),
                isNull());
    }

    /**
     * La página y el tamaño se entregan como texto para validarse con los mensajes del formulario
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void list_pageAndSizeAreForwardedAsTextToBeValidatedWithTheFormMessages() throws Exception {
        when(userService.search(any(RequesterIdentity.class), isNull(), isNull(), isNull(), eq("abc"), eq("500")))
                .thenThrow(new InvalidRequestException("Datos inválidos.", List.of(
                        new ErrorResponse.ErrorDetail("page", "Página no válida."),
                        new ErrorResponse.ErrorDetail("size", "Tamaño de página no válido (1 a 100)."))));

        mockMvc.perform(get(URL)
                .header(ID_HEADER, ADMIN_ID.toString())
                .header(ROLE_HEADER, ADMIN_ROLE)
                .param("page", "abc")
                .param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].campo").value("page"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Página no válida."))
                .andExpect(jsonPath("$.errores[1].campo").value("size"));
    }

    /**
     * El listado sin permiso responde 403
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void list_withoutPermission_returns403() throws Exception {
        when(userService.search(any(RequesterIdentity.class), any(), any(), any(), any(), any()))
                .thenThrow(new ForbiddenOperationException("No tiene permisos para realizar esta operación."));

        mockMvc.perform(get(URL)
                .header(ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "ESTUDIANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    /**
     * Sin cabeceras de identidad la respuesta es 401 y no se llama al servicio
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void list_withoutIdentityHeaders_returns401AndDoesNotCallTheService() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        verifyNoInteractions(userService);
    }

    /**
     * Una cabecera de roles inválida responde 401
     *
     * @throws Exception si falla la petición simulada o la lectura de la respuesta
     */
    @Test
    void list_invalidRoleHeader_returns401() throws Exception {
        mockMvc.perform(get(URL)
                .header(ID_HEADER, ADMIN_ID.toString())
                .header(ROLE_HEADER, "SUPERMAN"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }
}
