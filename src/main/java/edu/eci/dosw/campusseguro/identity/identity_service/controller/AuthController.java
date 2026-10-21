package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserCreateRequest;
import edu.eci.dosw.campusseguro.identity.identity_service.dto.UserResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private UserService userService;

    public AuthController(UserService userService){
        this.userService = userService;
    }

        /**
     * 
     * @param request
     * @return
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request){
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
