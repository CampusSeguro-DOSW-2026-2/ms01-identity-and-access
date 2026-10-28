package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeStatusResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Consulta y administración de usuarios (RF-MS01-07) y gestión del perfil propio (RF-MS01-06), la
 * identidad del solicitante llega siempre en las cabeceras que inyecta el API Gateway
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    /**
     * Crea el controlador con su servicio
     *
     * @param userService servicio de usuarios
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/v1/users: lista y busca usuarios con paginación (RF-MS01-07, formulario F-07)
     *
     * 200 página de usuarios, 400 parámetros inválidos, 401 identidad ausente, 403 sin permiso
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param correo búsqueda parcial por correo
     * @param estado filtro por estado
     * @param rol filtro por rol
     * @param page número de página, se recibe como texto para validarlo con el mensaje del formulario
     * @param size tamaño de página, se recibe como texto para validarlo con el mensaje del formulario
     * @return la página de usuarios
     */
    @GetMapping
    public ResponseEntity<PageResponse<UserProfileResponse>> searchUsers(
            RequesterIdentity requester,
            @RequestParam(required = false) String correo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return ResponseEntity.ok(userService.search(requester, correo, estado, rol, page, size));
    }

    /**
     * PATCH /api/v1/users/status: activa o inactiva a uno o varios usuarios (RF-MS01-07, formulario F-08)
     *
     * 200 estado anterior y nuevo por usuario, 400 datos inválidos, 401 identidad ausente, 403 sin permiso
     * o intento de cambiar el propio estado, 404 algún usuario no existe
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param request usuarios objetivo y nuevo estado
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el estado anterior y el nuevo de cada usuario
     */
    @PatchMapping("/status")
    public ResponseEntity<ChangeStatusResponse> changeUserStatus(
            RequesterIdentity requester,
            @RequestBody ChangeStatusRequest request,
            HttpServletRequest httpRequest) {
        ChangeStatusResponse response = userService.changeUserStatus(requester, request,
                CorrelationIds.current(httpRequest));
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/users/me: consulta el perfil propio (RF-MS01-06)
     *
     * 200 perfil del solicitante, 401 identidad ausente, 403 sin permiso, 404 usuario inexistente
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @return el perfil del solicitante
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(RequesterIdentity requester) {
        return ResponseEntity.ok(userService.getMyProfile(requester));
    }

    /**
     * PUT /api/v1/users/me: actualiza el perfil propio (RF-MS01-06, formulario F-03)
     *
     * 200 perfil actualizado, 400 datos inválidos o campos no editables, 401 identidad ausente, 403 sin
     * permiso, 404 usuario inexistente
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param request nombres y apellidos nuevos
     * @return el perfil actualizado
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            RequesterIdentity requester,
            @RequestBody UserUpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(requester, request));
    }

    /**
     * GET /api/v1/users/{id}: consulta un usuario por su id (RF-MS01-07)
     *
     * 200 usuario, 400 id con formato inválido, 401 identidad ausente, 403 sin permiso, 404 usuario
     * inexistente
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param id id del usuario consultado
     * @return los datos del usuario
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getUserById(RequesterIdentity requester, @PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(requester, id));
    }
}
