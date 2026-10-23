package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserUpdateProfileRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserProfileResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private UserService userService;

    public UserController(UserService userService){
        this.userService = userService;
    }

    /**
     * 
     * @param requesterId
     * @param correo
     * @param estado
     * @param rol
     * @param page
     * @param size
     * @return
     */
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> searchUsers(@RequestHeader("X-User-Id") UUID requesterId, @RequestHeader("X-User-Role") String userRole, @RequestParam(required = false) String correo, @RequestParam(required = false) String estado, @RequestParam(required = false) String rol, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size){
        PageResponse<UserResponse> response = userService.search(requesterId, userRole, correo, estado, rol, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * 
     * @param requesterId
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getUserById(@RequestHeader("X-User-Id") UUID requesterId, @RequestHeader("X-User-Role") String userRole, @PathVariable UUID id){
        return ResponseEntity.ok(userService.getUserById(requesterId, userRole, id));
    }

    /**
     * 
     * @param id
     * @return
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@RequestHeader("X-User-Id") UUID id){
        return ResponseEntity.ok(userService.getMyProfile(id));
    }

    /**
     * 
     * @param id
     * @param request
     * @return
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(@RequestHeader("X-User-Id") UUID id, @Valid @RequestBody UserUpdateProfileRequest request){
        return ResponseEntity.ok(userService.updateProfile(id, request));
    }
}
