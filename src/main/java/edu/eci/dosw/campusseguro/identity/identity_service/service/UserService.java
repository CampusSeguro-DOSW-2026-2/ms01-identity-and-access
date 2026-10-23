package edu.eci.dosw.campusseguro.identity.identity_service.service;

import java.util.List;
import java.util.UUID;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;

public interface UserService {
    
    List<UserResponse> findAll();

    UserResponse findById(UUID id);

    UserResponse getUserById(UUID requesterId, String userRole, UUID id);

    PageResponse<UserResponse> search(UUID requesterId, String userRole, String correo, String estado, String rol, int page, int size);

    UserResponse create(UserCreateRequest request);

    UserProfileResponse getMyProfile(UUID id);

    UserProfileResponse updateProfile(UUID id, UserUpdateProfileRequest request);

}
