package edu.eci.dosw.campusseguro.identity.identity_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
import java.util.List;
import java.util.UUID;
 
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import edu.eci.dosw.campusseguro.identity.identity_service.controller.AuthController;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.EmailAlreadyExistsException;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {
    private static final String URL = "/api/v1/auth/register";
 
    @Mock
    private UserService userService;
 
    @InjectMocks
    private AuthController authController;
 
    private MockMvc mockMvc;
 
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String body(String nombre, String correo, String password) {
        return """
                {
                  "primer_nombre": "%s",
                  "primer_apellido": "Gomez",
                  "correo": "%s",
                  "password": "%s"
                }
                """.formatted(nombre, correo, password);
    }
 
    private UserResponse buildResponse() {
        UserResponse response = new UserResponse();
        response.setId(UUID.randomUUID());
        response.setFirstName("Carlos");
        response.setFirstLastName("Gomez");
        response.setEmail("carlos.gomez@mail.escuelaing.edu.co");
        response.setRoles(List.of(UserRole.ESTUDIANTE));
        response.setState(UserState.ACTIVO);
        return response;
    }
 
 
    @Test
    void register_validRequest_returns201WithoutPassword() throws Exception {
        when(userService.create(any(UserCreateRequest.class))).thenReturn(buildResponse());
 
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Carlos", "carlos.gomez@mail.escuelaing.edu.co", "MiPasswordSeguro123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.primer_nombre").value("Carlos"))
                .andExpect(jsonPath("$.correo").value("carlos.gomez@mail.escuelaing.edu.co"))
                .andExpect(jsonPath("$.roles[0]").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist());
 
        verify(userService).create(any(UserCreateRequest.class));
    }
 
    @Test
    void register_emailAlreadyExists_returns409() throws Exception {
        when(userService.create(any(UserCreateRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("carlos.gomez@mail.escuelaing.edu.co"));
 
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Carlos", "carlos.gomez@mail.escuelaing.edu.co", "MiPasswordSeguro123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }
 
    @ParameterizedTest(name = "dato inválido en: {3}")
    @CsvSource({
        "C,carlos.gomez@mail.escuelaing.edu.co,MiPasswordSeguro123,primer_nombre",
        "Carlos,abc,MiPasswordSeguro123,correo",
        "Carlos,carlos.gomez@mail.escuelaing.edu.co,123,password_hash",
        "Carlos,carlos@gmail.com,MiPasswordSeguro123,correo"
    })
    void register_invalidData_returns400AndDoesNotCallService(
            String nombre, String correo, String password, String campo) throws Exception {
 
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(nombre, correo, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$." + campo).exists());
 
        verifyNoInteractions(userService);
    }
 
    @Test
    void register_missingPassword_returns400() throws Exception {
        String json = """
                {
                  "primer_nombre": "Carlos",
                  "primer_apellido": "Gomez",
                  "correo": "carlos.gomez@mail.escuelaing.edu.co"
                }
                """;
 
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password_hash").exists());
 
        verifyNoInteractions(userService);
    }
}
