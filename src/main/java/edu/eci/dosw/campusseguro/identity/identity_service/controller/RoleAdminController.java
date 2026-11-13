package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.CreateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PermissionItemResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.RoleDetailResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRolePermissionsRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UpdateRoleRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.identity.identity_service.service.RoleAdminService;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Administración de roles y del catálogo de permisos (RF-MS01-10)
 */
@RestController
@RequestMapping("/api/v1")
public class RoleAdminController {

    public static final String ROLES_PATH = "/api/v1/roles/";

    private final RoleAdminService roleAdminService;

    /**
     * Crea el controlador con su servicio
     *
     * @param roleAdminService servicio de administración de roles y permisos
     */
    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    /**
     * GET /api/v1/roles/details: lista los roles con su detalle (RF-MS01-10)
     *
     * 200 roles ordenados por nombre, 401 identidad ausente o inválida, 403 sin permiso
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @return los roles con su descripción, sus permisos y la marca de sistema
     */
    @GetMapping("/roles/details")
    public ResponseEntity<List<RoleDetailResponse>> listRoles(RequesterIdentity requester) {
        return ResponseEntity.ok(roleAdminService.listRoles(requester));
    }

    /**
     * GET /api/v1/roles/{rol}: consulta un rol (RF-MS01-10)
     *
     * 200 el rol, 400 nombre con formato inválido, 401 identidad ausente, 403 sin permiso, 404 rol inexistente
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param rol nombre del rol, se normaliza
     * @return el detalle del rol
     */
    @GetMapping("/roles/{rol}")
    public ResponseEntity<RoleDetailResponse> getRole(RequesterIdentity requester, @PathVariable("rol") String rol) {
        return ResponseEntity.ok(roleAdminService.getRole(requester, rol));
    }

    /**
     * GET /api/v1/permissions: catálogo de permisos (RF-MS01-10)
     *
     * 200 permisos ordenados por código, 401 identidad ausente, 403 sin permiso
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @return cada permiso con su descripción
     */
    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionItemResponse>> listPermissions(RequesterIdentity requester) {
        return ResponseEntity.ok(roleAdminService.listPermissions(requester));
    }

    /**
     * POST /api/v1/roles: crea un rol (RF-MS01-10, formulario F-10)
     *
     * 201 rol creado con su ruta en Location, 400 datos inválidos o campo no permitido, 401 identidad
     * ausente, 403 sin permiso, 409 el nombre ya existe, 422 incluye ms01:role:manage
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param request nombre, descripción y permisos del rol
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el rol creado
     */
    @PostMapping("/roles")
    public ResponseEntity<RoleDetailResponse> createRole(
            RequesterIdentity requester,
            @RequestBody CreateRoleRequest request,
            HttpServletRequest httpRequest) {
        RoleDetailResponse created = roleAdminService.createRole(requester, request,
                CorrelationIds.current(httpRequest));
        return ResponseEntity.created(URI.create(ROLES_PATH + created.rol())).body(created);
    }

    /**
     * PUT /api/v1/roles/{rol}: cambia el nombre y la descripción de un rol (RF-MS01-10, formulario F-11)
     *
     * 200 rol actualizado, 400 datos inválidos o campo no permitido, 401 identidad ausente, 403 sin permiso,
     * 404 rol inexistente, 409 nombre repetido o rol de sistema renombrado
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param rol nombre actual del rol, se normaliza
     * @param request nombre y descripción nuevos
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el rol actualizado
     */
    @PutMapping("/roles/{rol}")
    public ResponseEntity<RoleDetailResponse> updateRole(
            RequesterIdentity requester,
            @PathVariable("rol") String rol,
            @RequestBody UpdateRoleRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(roleAdminService.updateRole(requester, rol, request,
                CorrelationIds.current(httpRequest)));
    }

    /**
     * PUT /api/v1/roles/{rol}/permissions: reemplaza los permisos de un rol (RF-MS01-10, formulario F-12)
     *
     * 200 rol actualizado, 400 lista inválida o campo no permitido, 401 identidad ausente, 403 sin permiso,
     * 404 rol inexistente, 422 incumple la regla de ms01:role:manage
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param rol nombre del rol, se normaliza
     * @param request lista completa de permisos
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return el rol actualizado
     */
    @PutMapping("/roles/{rol}/permissions")
    public ResponseEntity<RoleDetailResponse> updatePermissions(
            RequesterIdentity requester,
            @PathVariable("rol") String rol,
            @RequestBody UpdateRolePermissionsRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(roleAdminService.updatePermissions(requester, rol, request,
                CorrelationIds.current(httpRequest)));
    }

    /**
     * DELETE /api/v1/roles/{rol}: borra un rol (RF-MS01-10, formulario F-13)
     *
     * 204 rol borrado sin cuerpo, 400 nombre con formato inválido, 401 identidad ausente, 403 sin permiso,
     * 404 rol inexistente, 409 rol de sistema
     *
     * @param requester identidad del solicitante tomada de las cabeceras
     * @param rol nombre del rol, se normaliza
     * @param httpRequest petición en curso, de la que se obtiene el correlation id
     * @return respuesta vacía
     */
    @DeleteMapping("/roles/{rol}")
    public ResponseEntity<Void> deleteRole(
            RequesterIdentity requester,
            @PathVariable("rol") String rol,
            HttpServletRequest httpRequest) {
        roleAdminService.deleteRole(requester, rol, CorrelationIds.current(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
