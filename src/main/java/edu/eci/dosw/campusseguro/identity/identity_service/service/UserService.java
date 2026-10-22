package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

public interface UserService {
    
    List<UserResponse> findAll();

    UserResponse findById(UUID id);

    UserResponse findByEmail(String email);

    UserResponse findByState(UserState state);

    UserResponse findByRole(UserRole rol);

    UserResponse create(UserCreateRequest request);

}
