package edu.eci.dosw.login.controller;

import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;
import edu.eci.dosw.login.dto.RegisterUpdateRequest;
import edu.eci.dosw.login.dto.RoleResponse;
import edu.eci.dosw.login.dto.UserPatchRequest;
import edu.eci.dosw.login.entity.UserRol;
import edu.eci.dosw.login.service.AccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestion de usuarios y catalogo de roles para el frontend.
 *
 * FASE 1 (MVP): endpoints abiertos (SecurityConfig usa permitAll).
 * FASE 2: se protegeran con roles/permisos.
 */
@Tag(name = "Usuarios y roles", description = "Gestion de usuarios y catalogo de roles")
@RestController
@RequestMapping("/api/v1")
public class AccessController {

    private final AccessService accessService;

    public AccessController(AccessService accessService) {
        this.accessService = accessService;
    }

    @Operation(summary = "Listar todos los usuarios")
    @GetMapping("/users")
    public List<RegisterResponse> findAll() {
        return accessService.findAll();
    }

    @Operation(summary = "Consultar un usuario por id")
    @GetMapping("/users/{id}")
    public RegisterResponse findById(@PathVariable Long id) {
        return accessService.findById(id);
    }

    @Operation(summary = "Crear un usuario")
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse create(@Valid @RequestBody RegisterRequest request) {
        return accessService.register(request);
    }

    @Operation(summary = "Actualizar por completo un usuario")
    @PutMapping("/users/{id}")
    public RegisterResponse update(@PathVariable Long id, @Valid @RequestBody RegisterUpdateRequest request) {
        return accessService.update(request, id);
    }

    @Operation(summary = "Actualizar parcialmente un usuario")
    @PatchMapping("/users/{id}")
    public RegisterResponse patch(@PathVariable Long id, @Valid @RequestBody UserPatchRequest request) {
        return accessService.patch(request, id);
    }

    @Operation(summary = "Asignar un rol a un usuario")
    @PostMapping("/users/{id}/roles")
    public RegisterResponse assignRol(@PathVariable Long id, @RequestParam UserRol rol) {
        return accessService.assignRol(id, rol);
    }

    @Operation(summary = "Eliminar un usuario")
    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        accessService.delete(id);
    }

    @Operation(summary = "Listar los roles disponibles")
    @GetMapping("/roles")
    public List<RoleResponse> findRoles() {
        return accessService.findRoles();
    }
}