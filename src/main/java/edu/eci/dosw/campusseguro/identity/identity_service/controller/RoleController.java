package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.ChangeRolesResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RolePermissionsResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.security.IdentityHeaderParser;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleService;

/**
 * Consulta de roles con sus permisos y cambio de roles de los usuarios (RF-MS01-05)
 */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService roleService;

    /**
     *
     * @param roleService servicio de gestion de roles y permisos
     */
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * GET /api/v1/roles: roles con sus permisos. El ADMIN recibe los ocho roles actuales, cualquier
     * otro usuario autenticado recibe solo sus propios roles.
     *
     * 200 arreglo de roles con sus permisos
     * 401 faltan o no son validas las cabeceras de identidad.
     *
     * @param userId id del usuario
     * @param roles rol del usuario
     * @return la lista de roles y los codigos de sus permisos
     */
    @GetMapping("/roles")
    public ResponseEntity<List<RolePermissionsResponse>> getRoles(
            @RequestHeader(value = IdentityHeaderParser.USER_ID_HEADER, required = false) String userId,
            @RequestHeader(value = IdentityHeaderParser.USER_ROLE_HEADER, required = false) String roles) {
        RequesterIdentity requester = IdentityHeaderParser.parse(userId, roles);
        return ResponseEntity.ok(roleService.getRoles(requester));
    }

    /**
     * PATCH /api/v1/users/roles: agrega y retira roles a uno o varios usuarios (solo ADMINISTRADOR)
     *
     * 200: roles resultantes por usuario
     * 
     * 400: datos invalidos, usuario que quedaria sin roles
     * 
     * 401:identidad ausente
     * 
     * 403: no es administrador o intenta modificar sus propios roles
     * 
     * 404: algun usuario no existe
     *
     * @param userId id del usuario
     * @param roles roles del usuario
     * @param correlationId correlationId
     * @param request usuarios objetivo y roles a agregar y a retirar
     * @return roles finales de cada usuario
     */
    @PatchMapping("/users/roles")
    public ResponseEntity<ChangeRolesResponse> changeRoles(
            @RequestHeader(value = IdentityHeaderParser.USER_ID_HEADER, required = false) String userId,
            @RequestHeader(value = IdentityHeaderParser.USER_ROLE_HEADER, required = false) String roles,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody ChangeRolesRequest request) {
        RequesterIdentity requester = IdentityHeaderParser.parse(userId, roles);
        return ResponseEntity.ok(roleService.changeRoles(requester, request, correlationId));
    }
}
