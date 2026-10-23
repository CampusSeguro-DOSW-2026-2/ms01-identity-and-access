package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserRole;
import edu.eci.dosw.campusseguro.identity.identity_service.entity.UserState;

public interface UserService {
    
    List<UserResponse> findAll();

    UserResponse findById(UUID id);

    List<UserResponse> findByEmail(String email);

    List<UserResponse> findByState(UserState state);

    List<UserResponse> findByRole(UserRole role);

    UserResponse create(UserCreateRequest request);

    UserProfileResponse getMyProfile(UUID id);

    UserProfileResponse updateProfile(UUID id, UserUpdateProfileRequest request);

}
