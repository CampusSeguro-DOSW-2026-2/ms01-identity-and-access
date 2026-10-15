package edu.eci.dosw.login.service;

import java.util.List;

import edu.eci.dosw.login.dto.LoginRequest;
import edu.eci.dosw.login.dto.LoginResponse;
import edu.eci.dosw.login.dto.RegisterRequest;
import edu.eci.dosw.login.dto.RegisterResponse;
import edu.eci.dosw.login.dto.RegisterUpdateRequest;
import edu.eci.dosw.login.dto.RoleResponse;
import edu.eci.dosw.login.dto.UserPatchRequest;
import edu.eci.dosw.login.entity.UserRol;

/**
 * Servicio del dominio de identidad y acceso (registro, login y gestion de usuarios).
 */
public interface AccessService {

    List<RegisterResponse> findAll();

    RegisterResponse findById(Long id);

    RegisterResponse findByEmail(String email);

    RegisterResponse register(RegisterRequest request);

    RegisterResponse update(RegisterUpdateRequest request, Long id);

    RegisterResponse patch(UserPatchRequest request, Long id);

    RegisterResponse assignRol(Long id, UserRol rol);

    LoginResponse login(LoginRequest request);

    void delete(Long id);

    List<RoleResponse> findRoles();
}