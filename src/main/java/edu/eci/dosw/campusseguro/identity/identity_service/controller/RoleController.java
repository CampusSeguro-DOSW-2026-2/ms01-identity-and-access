package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Consulta de roles con sus permisos y cambio de roles de los usuarios (RF-MS01-05)
 */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService roleService;

    /**
     * Crea el controlador con su servicio
     *
     * @param roleService servicio de gestión de roles y permisos
     */
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * GET /api/v1/roles: roles con sus permisos (RF-MS01-05)
     *
     * 200 arreglo de roles con sus permisos, 401 identidad ausente o inválida
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @return la lista de roles visibles para el solicitante y los códigos de sus permisos
     */
    @GetMapping("/roles")
    public ResponseEntity<List<RolePermissionsResponse>> getRoles(RequesterIdentity requester) {
        return ResponseEntity.ok(roleService.getRoles(requester));
    }

    /**
     * PATCH /api/v1/users/roles: agrega y retira roles a uno o varios usuarios (RF-MS01-05, formulario F-09)
     *
     * 200 roles resultantes por usuario, 400 datos inválidos o usuario que quedaría sin roles, 401
     * identidad ausente, 403 sin permiso o intento de modificar los propios roles, 404 algún usuario no
     * existe
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param request usuarios objetivo y roles a agregar y a retirar
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return los roles finales de cada usuario
     */
    @PatchMapping("/users/roles")
    public ResponseEntity<ChangeRolesResponse> changeRoles(
            RequesterIdentity requester,
            @RequestBody ChangeRolesRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(roleService.changeRoles(requester, request, CorrelationIds.current(httpRequest)));
    }
}
